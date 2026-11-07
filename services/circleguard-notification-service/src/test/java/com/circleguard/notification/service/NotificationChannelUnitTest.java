package com.circleguard.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class NotificationChannelsUnitTest {

    private final AuditLogService audit = mock(AuditLogService.class);

    // ---- SMS -------------------------------------------------------------------

    private SmsServiceImpl sms(String accountSid) {
        SmsServiceImpl service = new SmsServiceImpl();
        ReflectionTestUtils.setField(service, "accountSid", accountSid);
        ReflectionTestUtils.setField(service, "authToken", "token");
        ReflectionTestUtils.setField(service, "fromNumber", "+15550000000");
        ReflectionTestUtils.setField(service, "auditLogService", audit);
        return service;
    }

    @Test
    void mockSmsIsLoggedAndAuditedWithoutCallingTwilio() throws Exception {
        SmsServiceImpl service = sms("AC_MOCK_SID");
        service.init();

        service.sendAsync("anon-1", "Your status changed").get();

        verify(audit).logDelivery(eq("anon-1"), eq("SMS"), eq("SUCCESS"), anyString());
    }

    @Test
    void realSmsFailureIsAuditedForRetry() {
        // Twilio.init is never called, so Twilio refuses to send without credentials.
        SmsServiceImpl service = sms("AC0123456789abcdef0123456789abcdef");

        assertThatThrownBy(() -> service.sendAsync("anon-1", "msg")).isInstanceOf(RuntimeException.class);
        verify(audit).logDelivery(eq("anon-1"), eq("SMS"), eq("RETRY"), anyString());
        verify(audit, never()).logDelivery(eq("anon-1"), eq("SMS"), eq("SUCCESS"), anyString());
    }

    @Test
    void smsRecoveryAuditsTheFinalFailure() {
        var result = sms("AC_MOCK_SID").recover(new IllegalStateException("twilio down"), "anon-1", "msg");

        assertThat(result).isCompletedExceptionally();
        verify(audit).logDelivery("anon-1", "SMS", "FAILED", null);
    }

    // ---- Push ------------------------------------------------------------------

    private PushServiceImpl push(String token, HttpStatus status, List<ClientRequest> requests) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(status).build());
        });
        PushServiceImpl service = new PushServiceImpl(builder, "http://gotify:80");
        ReflectionTestUtils.setField(service, "gotifyToken", token);
        ReflectionTestUtils.setField(service, "auditLogService", audit);
        return service;
    }

    @Test
    void mockPushIsAuditedWithoutCallingGotify() throws Exception {
        List<ClientRequest> requests = new ArrayList<>();

        push("MOCK_TOKEN", HttpStatus.OK, requests).sendAsync("anon-1", "Alert").get();

        assertThat(requests).isEmpty();
        verify(audit).logDelivery(eq("anon-1"), eq("PUSH"), eq("SUCCESS"), anyString());
    }

    @Test
    void realPushIsPostedToGotify() throws Exception {
        List<ClientRequest> requests = new ArrayList<>();
        PushServiceImpl service = push("real-token", HttpStatus.OK, requests);

        service.sendAsync("anon-1", "Alert", Map.of("url", "circleguard://guidelines")).get();
        service.sendAsync("anon-2", "Alert").get();

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).method().name()).isEqualTo("POST");
        assertThat(requests.get(0).url().toString()).isEqualTo("http://gotify:80/message?token=real-token");
        verify(audit).logDelivery(eq("anon-1"), eq("PUSH"), eq("SUCCESS"), anyString());
        verify(audit).logDelivery(eq("anon-2"), eq("PUSH"), eq("SUCCESS"), anyString());
    }

    @Test
    void rejectedPushIsAuditedForRetry() {
        PushServiceImpl service = push("real-token", HttpStatus.INTERNAL_SERVER_ERROR, new ArrayList<>());

        assertThatThrownBy(() -> service.sendAsync("anon-1", "Alert", Map.of())).isInstanceOf(RuntimeException.class);
        verify(audit).logDelivery(eq("anon-1"), eq("PUSH"), eq("RETRY"), anyString());
    }

    @Test
    void pushRecoveryAuditsTheFinalFailure() {
        var result = push("real-token", HttpStatus.OK, new ArrayList<>())
                .recover(new IllegalStateException("gotify down"), "anon-1", "Alert", Map.of());

        assertThat(result).isCompletedExceptionally();
        verify(audit).logDelivery("anon-1", "PUSH", "FAILED", null);
    }

    // ---- Templates ---------------------------------------------------------------

    @Test
    void emailFallsBackToPlainTextWhenTheTemplateCannotBeLoaded() throws Exception {
        Configuration freemarker = mock(Configuration.class);
        when(freemarker.getTemplate("health_alert.ftl")).thenThrow(new IOException("missing"));
        TemplateService templates = new TemplateService(freemarker);

        assertThat(templates.generateEmailContent("SUSPECT", null))
                .isEqualTo("CircleGuard Health Update: Your status is now SUSPECT. Please check the app for instructions.");
    }

    @Test
    void pushAndSmsTextsDependOnTheStatus() {
        TemplateService templates = new TemplateService(mock(Configuration.class));
        ReflectionTestUtils.setField(templates, "guidelinesDeepLink", "circleguard://guidelines");

        assertThat(templates.generatePushContent("SUSPECT")).contains("isolation steps");
        assertThat(templates.generatePushContent("CONFIRMED")).isEqualTo("CircleGuard: Your health status has been updated to CONFIRMED");
        assertThat(templates.generatePushMetadata("PROBABLE")).containsEntry("url", "circleguard://guidelines");
        assertThat(templates.generatePushMetadata("CONFIRMED")).isEmpty();
        assertThat(templates.generateSmsContent("PROBABLE")).contains("PROBABLE");
    }

    // ---- Exposure listener -------------------------------------------------------

    @Test
    void exposureListenerNotifiesOnlyRiskStatuses() {
        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        LmsService lms = mock(LmsService.class);
        ExposureNotificationListener listener = new ExposureNotificationListener(dispatcher, new ObjectMapper(), lms);

        listener.handleStatusChange("{\"anonymousId\":\"anon-1\",\"status\":\"SUSPECT\"}");
        listener.handleStatusChange("{\"anonymousId\":\"anon-2\",\"status\":\"ACTIVE\"}");
        listener.handleStatusChange("{\"anonymousId\":\"anon-3\"}");
        listener.handleStatusChange("not json");

        verify(dispatcher).dispatch("anon-1", "SUSPECT");
        verify(lms).syncRemoteAttendance("anon-1", "SUSPECT");
        verify(dispatcher, never()).dispatch(eq("anon-2"), anyString());
        verify(dispatcher, never()).dispatch(eq("anon-3"), anyString());
    }

    @Test
    void exposureListenerIgnoresEventsItCannotParse() {
        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        LmsService lms = mock(LmsService.class);

        new ExposureNotificationListener(dispatcher, new ObjectMapper(), lms).handleStatusChange("{broken");

        verifyNoInteractions(dispatcher, lms);
    }
}