package com.circleguard.form.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class HealthSurveyServiceTest {

    private HealthSurveyRepository repository;
    private QuestionnaireService questionnaireService;
    private SymptomMapper symptomMapper;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private HealthSurveyService service;
    private final UUID anonymousId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(HealthSurveyRepository.class);
        questionnaireService = mock(QuestionnaireService.class);
        symptomMapper = mock(SymptomMapper.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        service = new HealthSurveyService(repository, questionnaireService, symptomMapper, kafkaTemplate);
        when(repository.save(any(HealthSurvey.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentEvent(String topic) {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(topic), eq(anonymousId.toString()), event.capture());
        return (Map<String, Object>) event.getValue();
    }

    @Test
    void surveyWithSymptomsIsSavedAndPublished() {
        Questionnaire active = Questionnaire.builder().title("Daily").build();
        HealthSurvey survey = HealthSurvey.builder().anonymousId(anonymousId).build();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.of(active));
        when(symptomMapper.hasSymptoms(survey, active)).thenReturn(true);

        HealthSurvey saved = service.submitSurvey(survey);

        assertThat(saved.getHasFever()).isTrue();
        assertThat(saved.getHasCough()).isTrue();
        assertThat(saved.getValidationStatus()).isNull();
        Map<String, Object> event = sentEvent("survey.submitted");
        assertThat(event).containsEntry("anonymousId", anonymousId).containsEntry("hasSymptoms", true).containsKey("timestamp");
    }

    @Test
    void withoutAnActiveQuestionnaireTheSurveyHasNoSymptoms() {
        HealthSurvey survey = HealthSurvey.builder().anonymousId(anonymousId).hasFever(true).build();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());

        HealthSurvey saved = service.submitSurvey(survey);

        assertThat(saved.getHasFever()).as("explicit legacy field is kept").isTrue();
        assertThat(saved.getHasCough()).isFalse();
        assertThat(sentEvent("survey.submitted")).containsEntry("hasSymptoms", false);
    }

    @Test
    void surveyWithACertificateWaitsForValidation() {
        HealthSurvey survey = HealthSurvey.builder().anonymousId(anonymousId).attachmentPath("cert.pdf").build();
        when(questionnaireService.getActiveQuestionnaire()).thenReturn(Optional.empty());

        assertThat(service.submitSurvey(survey).getValidationStatus()).isEqualTo(ValidationStatus.PENDING);
    }

    @Test
    void pendingSurveysAreThoseWithAPendingCertificate() {
        List<HealthSurvey> pending = List.of(HealthSurvey.builder().anonymousId(anonymousId).build());
        when(repository.findByAttachmentPathIsNotNullAndValidationStatus(ValidationStatus.PENDING)).thenReturn(pending);

        assertThat(service.getPendingSurveys()).isEqualTo(pending);
    }

    @Test
    void approvedCertificateIsRecordedAndPublished() {
        UUID surveyId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder().id(surveyId).anonymousId(anonymousId).build();
        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));

        service.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        assertThat(survey.getValidationStatus()).isEqualTo(ValidationStatus.APPROVED);
        assertThat(survey.getValidatedBy()).isEqualTo(adminId);
        verify(repository).save(survey);
        assertThat(sentEvent("certificate.validated"))
                .containsEntry("status", "APPROVED")
                .containsEntry("adminId", adminId)
                .containsEntry("anonymousId", anonymousId);
    }

    @Test
    void rejectedCertificateIsRecordedWithoutAnEvent() {
        UUID surveyId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder().id(surveyId).anonymousId(anonymousId).build();
        when(repository.findById(surveyId)).thenReturn(Optional.of(survey));

        service.validateSurvey(surveyId, ValidationStatus.REJECTED, UUID.randomUUID());

        assertThat(survey.getValidationStatus()).isEqualTo(ValidationStatus.REJECTED);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void validatingAnUnknownSurveyFails() {
        UUID surveyId = UUID.randomUUID();
        when(repository.findById(surveyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateSurvey(surveyId, ValidationStatus.APPROVED, UUID.randomUUID()))
                .hasMessageContaining(surveyId.toString());
    }
}
