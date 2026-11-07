package com.circleguard.promotion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.circleguard.promotion.support.Neo4jClientStub;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminAndStatsControllersUnitTest {

    private SystemSettingsRepository repository;
    private AdminController admin;

    @BeforeEach
    void setUp() {
        repository = mock(SystemSettingsRepository.class);
        when(repository.save(any(SystemSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        admin = new AdminController(repository);
    }

    @Test
    void missingSettingsAreInitialisedWithDefaults() {
        when(repository.getSettings()).thenReturn(Optional.empty());

        SystemSettings settings = admin.getSettings().getBody();

        assertThat(settings.getUnconfirmedFencingEnabled()).isTrue();
        assertThat(settings.getAutoThresholdSeconds()).isEqualTo(3600L);
        assertThat(settings.getMandatoryFenceDays()).isEqualTo(14);
        assertThat(settings.getEncounterWindowDays()).isEqualTo(14);
    }

    @Test
    void updatesChangeOnlyTheFieldsSent() {
        SystemSettings current = SystemSettings.builder().unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L).mandatoryFenceDays(14).encounterWindowDays(14).build();
        when(repository.getSettings()).thenReturn(Optional.of(current));

        admin.updateSettings(SystemSettings.builder().mandatoryFenceDays(10).build());
        SystemSettings updated = admin.updateSettings(SystemSettings.builder()
                .unconfirmedFencingEnabled(false).autoThresholdSeconds(1800L).encounterWindowDays(7).build()).getBody();

        assertThat(updated.getMandatoryFenceDays()).isEqualTo(10);
        assertThat(updated.getUnconfirmedFencingEnabled()).isFalse();
        assertThat(updated.getAutoThresholdSeconds()).isEqualTo(1800L);
        assertThat(updated.getEncounterWindowDays()).isEqualTo(7);
        verify(repository, times(2)).save(current);
    }

    @Test
    void unconfirmedFencingCanBeToggled() {
        when(repository.getSettings()).thenReturn(Optional.empty());

        assertThat(admin.toggleUnconfirmedFencing(false).getBody().getUnconfirmedFencingEnabled()).isFalse();
    }

    @Test
    void statsAggregateCountsPerStatus() {
        Map<String, Object> unknown = new HashMap<>();
        unknown.put("status", null);
        unknown.put("total", 1);
        Neo4jClientStub neo4j = new Neo4jClientStub()
                .allResults(List.of(Map.of("status", "ACTIVE", "total", 10L), Map.of("status", "SUSPECT", "total", 2L), unknown));
        HealthStatsController controller = new HealthStatsController(neo4j.client);

        Map<String, Object> stats = controller.getStats().getBody();
        Map<String, Object> department = controller.getStatsByDepartment("engineering").getBody();

        assertThat(stats)
                .containsEntry("activeCount", 10L)
                .containsEntry("suspectCount", 2L)
                .containsEntry("unknownCount", 1L)
                .containsEntry("totalUsers", 13L)
                .containsKey("timestamp");
        assertThat(department).containsEntry("department", "engineering").containsEntry("totalUsers", 13L);
        assertThat(neo4j.bound("dept")).isEqualTo("engineering");
    }
}
