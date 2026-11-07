package com.circleguard.form.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.service.HealthSurveyService;
import com.circleguard.form.service.QuestionnaireService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Endpoints not exercised by the existing @WebMvcTest classes. */
class FormControllersUnitTest {

    @Test
    void questionnaireEndpointsDelegateToTheService() {
        QuestionnaireService service = mock(QuestionnaireService.class);
        QuestionnaireController controller = new QuestionnaireController(service);
        List<Questionnaire> all = List.of(Questionnaire.builder().title("A").build());
        UUID id = UUID.randomUUID();
        when(service.getAllQuestionnaires()).thenReturn(all);
        when(service.getActiveQuestionnaire()).thenReturn(Optional.empty());

        assertThat(controller.getAll().getBody()).isEqualTo(all);
        assertThat(controller.getActive().getStatusCode().value()).isEqualTo(404);
        assertThat(controller.activate(id).getStatusCode().value()).isEqualTo(200);
        verify(service).activateQuestionnaire(id);
    }

    @Test
    void certificateEndpointsDelegateToTheSurveyService() {
        HealthSurveyService service = mock(HealthSurveyService.class);
        CertificateValidationController controller = new CertificateValidationController(service);
        List<HealthSurvey> pending = List.of(HealthSurvey.builder().attachmentPath("cert.pdf").build());
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(service.getPendingSurveys()).thenReturn(pending);

        assertThat(controller.getPending().getBody()).isEqualTo(pending);
        assertThat(controller.validate(surveyId, ValidationStatus.APPROVED, adminId).getStatusCode().value()).isEqualTo(200);
        verify(service).validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);
    }
}
