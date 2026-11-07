package com.circleguard.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.dashboard.client.PromotionClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

class AnalyticsServiceTest {

    private JdbcTemplate jdbc;
    private PromotionClient promotionClient;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        promotionClient = mock(PromotionClient.class);
        service = new AnalyticsService(jdbc, promotionClient, new KAnonymityFilter());
    }

    @Test
    void campusSummaryComesFromThePromotionService() {
        Map<String, Object> stats = Map.of("totalUsers", 500L);
        when(promotionClient.getHealthStats()).thenReturn(stats);

        assertThat(service.getCampusSummary()).isEqualTo(stats);
        assertThat(service.getGlobalHealthStats()).isEqualTo(stats);
    }

    @Test
    void departmentStatsAreKAnonymised() {
        when(promotionClient.getHealthStatsByDepartment("physics"))
                .thenReturn(Map.of("activeCount", 40L, "suspectCount", 2L, "totalUsers", 42L));

        assertThat(service.getDepartmentStats("physics"))
                .containsEntry("activeCount", 40L)
                .containsEntry("suspectCount", "<5");
    }

    @Test
    void hoursWithFewEntriesAreMasked() {
        UUID location = UUID.randomUUID();
        Map<String, Object> busy = new HashMap<>(Map.of("hour", "08:00", "entry_count", 120L));
        Map<String, Object> quiet = new HashMap<>(Map.of("hour", "23:00", "entry_count", 2L));
        when(jdbc.queryForList(anyString(), eq(location))).thenReturn(List.of(busy, quiet));

        List<Map<String, Object>> trends = service.getEntryTrends(location);

        assertThat(trends.get(0)).containsEntry("entry_count", 120L).doesNotContainKey("note");
        assertThat(trends.get(1)).containsEntry("entry_count", "<5").containsKey("note");
    }

    @Test
    void timeSeriesUsesTheRequestedBucket() {
        List<Map<String, Object>> rows = List.of(Map.of("status", "ACTIVE", "total", 10L));
        when(jdbc.queryForList(contains("date_trunc('day'"), eq(7))).thenReturn(rows);

        assertThat(service.getTimeSeries("daily", 7)).isEqualTo(rows);
        service.getTimeSeries("hourly", 3);
        verify(jdbc).queryForList(contains("date_trunc('hour'"), eq(3));
    }

    @Test
    void timeSeriesFallsBackToSampleDataWhenTheTableIsMissing() {
        when(jdbc.queryForList(anyString(), eq(48)))
                .thenThrow(new BadSqlGrammarException("time-series", "SELECT", new java.sql.SQLException("no table")));

        List<Map<String, Object>> series = service.getTimeSeries("hourly", 48);

        assertThat(series).hasSize(24 * 4);
        assertThat(series).extracting(point -> point.get("status"))
                .containsOnly("ACTIVE", "SUSPECT", "PROBABLE", "CONFIRMED");
    }
}
