package com.circleguard.promotion.listener;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.circleguard.promotion.service.HealthStatusService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SurveyListenerCertificateTest {

    private final HealthStatusService healthStatusService = mock(HealthStatusService.class);
    private final SurveyListener listener = new SurveyListener(healthStatusService);

    @Test
    void approvedCertificateRestoresAccess() {
        listener.onCertificateValidated(Map.of("anonymousId", "u1", "status", "APPROVED"));

        verify(healthStatusService).updateStatus("u1", "ACTIVE");
    }

    @Test
    void otherCertificateOutcomesAreIgnored() {
        listener.onCertificateValidated(Map.of("anonymousId", "u1", "status", "REJECTED"));
        listener.onCertificateValidated(Map.of("status", "APPROVED"));

        verify(healthStatusService, never()).updateStatus(anyString(), anyString());
    }

    @Test
    void processingErrorsAreSwallowed() {
        doThrow(new IllegalStateException("fence")).when(healthStatusService).updateStatus("u1", "ACTIVE");
        listener.onCertificateValidated(Map.of("anonymousId", "u1", "status", "APPROVED"));

        doThrow(new IllegalStateException("db")).when(healthStatusService).updateStatus("u1", "SUSPECT");
        listener.onSurveySubmitted(Map.of("anonymousId", "u1", "hasSymptoms", true));

        listener.onSurveySubmitted(Map.of("anonymousId", 42, "hasSymptoms", true));
        listener.onCertificateValidated(Map.of("anonymousId", 42, "status", "APPROVED"));
    }
}
