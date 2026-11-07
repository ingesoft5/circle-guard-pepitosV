package com.circleguard.form.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.form.model.Question;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.repository.QuestionnaireRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestionnaireServiceTest {

    private QuestionnaireRepository repository;
    private QuestionnaireService service;

    @BeforeEach
    void setUp() {
        repository = mock(QuestionnaireRepository.class);
        service = new QuestionnaireService(repository);
    }

    @Test
    void listsAllQuestionnaires() {
        List<Questionnaire> all = List.of(Questionnaire.builder().title("A").build());
        when(repository.findAll()).thenReturn(all);

        assertThat(service.getAllQuestionnaires()).isEqualTo(all);
    }

    @Test
    void activeQuestionnaireIsTheLatestActiveVersion() {
        Questionnaire active = Questionnaire.builder().title("v3").build();
        when(repository.findFirstByIsActiveTrueOrderByVersionDesc()).thenReturn(Optional.of(active));

        assertThat(service.getActiveQuestionnaire()).contains(active);
    }

    @Test
    void savingLinksEveryQuestionToItsQuestionnaire() {
        Question first = Question.builder().text("Fever?").build();
        Question second = Question.builder().text("Cough?").build();
        Questionnaire questionnaire = Questionnaire.builder().title("Daily").questions(List.of(first, second)).build();
        when(repository.save(questionnaire)).thenReturn(questionnaire);

        assertThat(service.saveQuestionnaire(questionnaire)).isSameAs(questionnaire);
        assertThat(first.getQuestionnaire()).isSameAs(questionnaire);
        assertThat(second.getQuestionnaire()).isSameAs(questionnaire);
    }

    @Test
    void savingAQuestionnaireWithoutQuestionsWorks() {
        Questionnaire questionnaire = Questionnaire.builder().title("Empty").build();
        when(repository.save(questionnaire)).thenReturn(questionnaire);

        assertThat(service.saveQuestionnaire(questionnaire)).isSameAs(questionnaire);
    }

    @Test
    void activatingDeactivatesTheOthers() {
        UUID targetId = UUID.randomUUID();
        Questionnaire previouslyActive = Questionnaire.builder().title("old").isActive(true).build();
        Questionnaire inactive = Questionnaire.builder().title("other").isActive(false).build();
        Questionnaire target = Questionnaire.builder().id(targetId).title("new").isActive(false).build();
        when(repository.findAll()).thenReturn(List.of(previouslyActive, inactive, target));
        when(repository.findById(targetId)).thenReturn(Optional.of(target));

        service.activateQuestionnaire(targetId);

        assertThat(previouslyActive.getIsActive()).isFalse();
        assertThat(target.getIsActive()).isTrue();
        verify(repository).save(previouslyActive);
        verify(repository).save(target);
        verify(repository, never()).save(inactive);
    }

    @Test
    void activatingAnUnknownQuestionnaireOnlyDeactivatesTheOthers() {
        UUID unknown = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of());
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        service.activateQuestionnaire(unknown);

        verify(repository, never()).save(any());
    }
}
