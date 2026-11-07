package com.circleguard.form.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SymptomMapperRulesTest {

    private final SymptomMapper mapper = new SymptomMapper();

    private boolean answer(String text, QuestionType type, Object response) {
        UUID id = UUID.randomUUID();
        Question question = Question.builder().id(id).text(text).type(type).build();
        HealthSurvey survey = HealthSurvey.builder().responses(Map.of(id.toString(), response)).build();
        return mapper.hasSymptoms(survey, Questionnaire.builder().questions(List.of(question)).build());
    }

    @Test
    void yesToABreathingQuestionIsASymptom() {
        assertThat(answer("Any difficulty BREATHING?", QuestionType.YES_NO, "yes")).isTrue();
    }

    @Test
    void yesToAnUnrelatedQuestionIsNotASymptom() {
        assertThat(answer("Did you travel abroad?", QuestionType.YES_NO, "YES")).isFalse();
    }

    @Test
    void anySelectionInASymptomsChoiceQuestionIsASymptom() {
        assertThat(answer("Which symptoms do you have?", QuestionType.MULTI_CHOICE, "[\"Headache\"]")).isTrue();
        assertThat(answer("Select your symptoms", QuestionType.SINGLE_CHOICE, "Fatigue")).isTrue();
    }

    @Test
    void emptySelectionsAndOtherChoiceQuestionsAreNotSymptoms() {
        assertThat(answer("Which symptoms do you have?", QuestionType.MULTI_CHOICE, "[]")).isFalse();
        assertThat(answer("Which symptoms do you have?", QuestionType.MULTI_CHOICE, "")).isFalse();
        assertThat(answer("Which building are you in?", QuestionType.SINGLE_CHOICE, "B")).isFalse();
        assertThat(answer("Describe your symptoms", QuestionType.TEXT, "none")).isFalse();
    }

    @Test
    void unansweredQuestionsAndMissingDataAreNotSymptoms() {
        Question question = Question.builder().id(UUID.randomUUID()).text("Fever?").type(QuestionType.YES_NO).build();
        Questionnaire questionnaire = Questionnaire.builder().questions(List.of(question)).build();

        assertThat(mapper.hasSymptoms(HealthSurvey.builder().responses(Map.of("other", "YES")).build(), questionnaire)).isFalse();
        assertThat(mapper.hasSymptoms(HealthSurvey.builder().build(), questionnaire)).isFalse();
        assertThat(mapper.hasSymptoms(HealthSurvey.builder().responses(Map.of()).build(), null)).isFalse();
        assertThat(mapper.hasSymptoms(HealthSurvey.builder().responses(Map.of()).build(), new Questionnaire())).isFalse();
    }
}
