package com.circleguard.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class AuditAndCircleFencedTest {

    @Test
    @SuppressWarnings("unchecked")
    void deliveryAuditEventsArePublished() {
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        AuditLogService audit = new AuditLogService(kafka);

        audit.logDelivery("anon-1", "EMAIL", "SUCCESS", "corr-1");
        audit.logDelivery("anon-2", "SMS", "FAILED", null);

        ArgumentCaptor<Object> first = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("notification.audit"), eq("anon-1"), first.capture());
        assertThat((Map<String, Object>) first.getValue())
                .containsEntry("channel", "EMAIL")
                .containsEntry("status", "SUCCESS")
                .containsEntry("correlationId", "corr-1")
                .containsKeys("eventId", "timestamp");
        ArgumentCaptor<Object> second = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("notification.audit"), eq("anon-2"), second.capture());
        assertThat(((Map<String, Object>) second.getValue()).get("correlationId")).isNotNull();
    }

    @Test
    void fencedCircleWithALocationCancelsTheRoom() {
        RoomReservationService rooms = mock(RoomReservationService.class);
        CircleFencedListener listener = new CircleFencedListener(new ObjectMapper(), rooms);

        listener.handleCircleFenced("{\"circleId\":\"42\",\"locationId\":\"room-101\"}");
        listener.handleCircleFenced("{\"circleId\":\"43\",\"locationId\":\"\"}");
        listener.handleCircleFenced("{\"circleId\":\"44\"}");
        listener.handleCircleFenced("not json");

        verify(rooms).cancelReservation("42", "room-101");
        verify(rooms, never()).cancelReservation(eq("43"), anyString());
        verify(rooms, never()).cancelReservation(eq("44"), any());
    }

    @Test
    void emailIsSentAndAudited() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AuditLogService audit = mock(AuditLogService.class);
        EmailServiceImpl email = new EmailServiceImpl(mailSender, audit);

        email.sendAsync("anon-1", "You may have been exposed").get();

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly("anon-1@example.com");
        assertThat(sent.getValue().getText()).isEqualTo("You may have been exposed");
        verify(audit).logDelivery(eq("anon-1"), eq("EMAIL"), eq("SUCCESS"), anyString());
    }

    @Test
    void failedEmailIsAuditedForRetryAndRecovery() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        AuditLogService audit = mock(AuditLogService.class);
        EmailServiceImpl email = new EmailServiceImpl(mailSender, audit);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> email.sendAsync("anon-1", "msg")).isInstanceOf(MailSendException.class);
        verify(audit).logDelivery(eq("anon-1"), eq("EMAIL"), eq("RETRY"), anyString());

        assertThat(email.recover(new MailSendException("smtp down"), "anon-1", "msg")).isCompletedExceptionally();
        verify(audit).logDelivery("anon-1", "EMAIL", "FAILED", null);
    }
}
