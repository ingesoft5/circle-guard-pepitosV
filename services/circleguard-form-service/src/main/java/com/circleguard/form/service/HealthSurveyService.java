package com.circleguard.form.service;

import com.circleguard.form.metrics.SurveyMetrics;
import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HealthSurveyService {
    private final HealthSurveyRepository repository;
    private final QuestionnaireService questionnaireService;
    private final SymptomMapper symptomMapper;
    private final OutboxService outboxService;
    private final SurveyMetrics surveyMetrics;

    private static final String TOPIC_SURVEY_SUBMITTED = "survey.submitted";
    private static final String TOPIC_CERTIFICATE_VALIDATED = "certificate.validated";

    @Transactional
    public HealthSurvey submitSurvey(HealthSurvey survey) {
        Optional<Questionnaire> activeQuestionnaire = questionnaireService.getActiveQuestionnaire();
        
        boolean hasSymptoms = activeQuestionnaire
                .map(q -> symptomMapper.hasSymptoms(survey, q))
                .orElse(false);
        
        // Compatibility: update the legacy fields if they are missing in request but present in responses
        if (survey.getHasFever() == null) survey.setHasFever(hasSymptoms);
        if (survey.getHasCough() == null) survey.setHasCough(hasSymptoms);
        
        // Set initial validation status if attachment is present
        if (survey.getAttachmentPath() != null) {
            survey.setValidationStatus(ValidationStatus.PENDING);
        }
        
        HealthSurvey saved = repository.save(survey);
        
        // HashMap, not Map.of: the Kafka type header must be a class the consumer can construct.
        Map<String, Object> event = new HashMap<>();
        event.put("anonymousId", saved.getAnonymousId());
        event.put("hasSymptoms", hasSymptoms);
        event.put("timestamp", System.currentTimeMillis());
        String aggregateId = saved.getId() != null
                ? saved.getId().toString()
                : saved.getAnonymousId().toString();
        outboxService.enqueue(
                "HealthSurvey", aggregateId, TOPIC_SURVEY_SUBMITTED,
                TOPIC_SURVEY_SUBMITTED, saved.getAnonymousId().toString(), event);
        surveyMetrics.recordSubmitted(hasSymptoms);
        
        return saved;
    }

    public List<HealthSurvey> getPendingSurveys() {
        return repository.findByAttachmentPathIsNotNullAndValidationStatus(ValidationStatus.PENDING);
    }

    @Transactional
    public void validateSurvey(UUID surveyId, ValidationStatus status, UUID adminId) {
        HealthSurvey survey = repository.findById(surveyId)
                .orElseThrow(() -> new RuntimeException("Survey not found: " + surveyId));
        
        survey.setValidationStatus(status);
        survey.setValidatedBy(adminId);
        repository.save(survey);

        // Emit event so promotion-service can restore access if approved
        if (status == ValidationStatus.APPROVED) {
            Map<String, Object> event = new HashMap<>();
            event.put("anonymousId", survey.getAnonymousId());
            event.put("status", "APPROVED");
            event.put("adminId", adminId);
            event.put("timestamp", System.currentTimeMillis());
            outboxService.enqueue(
                    "HealthSurvey", survey.getId().toString(), TOPIC_CERTIFICATE_VALIDATED,
                    TOPIC_CERTIFICATE_VALIDATED, survey.getAnonymousId().toString(), event);
        }
    }
}

