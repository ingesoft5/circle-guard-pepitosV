package com.circleguard.form.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuestionnaireLifecycleTest {

    @Test
    void newQuestionnaireDefaultsToAnInactiveFirstVersion() {
        Questionnaire questionnaire = Questionnaire.builder().title("Daily").build();

        questionnaire.onCreate();

        assertThat(questionnaire.getVersion()).isEqualTo(1);
        assertThat(questionnaire.getIsActive()).isFalse();
        assertThat(questionnaire.getCreatedAt()).isNotNull();
        assertThat(questionnaire.getUpdatedAt()).isNotNull();
    }

    @Test
    void explicitValuesAreKeptAndUpdatesRefreshTheTimestamp() {
        Questionnaire questionnaire = Questionnaire.builder().title("Daily").version(4).isActive(true).build();

        questionnaire.onCreate();
        questionnaire.setUpdatedAt(null);
        questionnaire.onUpdate();

        assertThat(questionnaire.getVersion()).isEqualTo(4);
        assertThat(questionnaire.getIsActive()).isTrue();
        assertThat(questionnaire.getUpdatedAt()).isNotNull();
    }
}
