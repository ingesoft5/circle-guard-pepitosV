package com.circleguard.promotion.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GraphCleanupTaskTest {

    private final UserNodeRepository repository = mock(UserNodeRepository.class);
    private final GraphCleanupTask task = new GraphCleanupTask(repository);

    @Test
    void purgesEncountersOlderThanFourteenDays() {
        when(repository.purgeStaleEncounters(anyLong())).thenReturn(3L);
        long before = System.currentTimeMillis();

        task.purgeStaleEncounters();

        ArgumentCaptor<Long> threshold = ArgumentCaptor.forClass(Long.class);
        verify(repository).purgeStaleEncounters(threshold.capture());
        long fourteenDays = 14L * 24 * 60 * 60 * 1000;
        assertThat(threshold.getValue()).isBetween(before - fourteenDays - 1000, before - fourteenDays + 1000);
    }

    @Test
    void failuresAndEmptyResultsDoNotBreakTheSchedule() {
        when(repository.purgeStaleEncounters(anyLong())).thenReturn(null);
        assertThatCode(task::purgeStaleEncounters).doesNotThrowAnyException();

        when(repository.purgeStaleEncounters(anyLong())).thenThrow(new IllegalStateException("neo4j down"));
        assertThatCode(task::purgeStaleEncounters).doesNotThrowAnyException();
    }
}
