package com.circleguard.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KAnonymityFilterTest {

    private final KAnonymityFilter filter = new KAnonymityFilter();

    @Test
    void smallCountsAreMaskedButLargeAndZeroCountsAreKept() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeCount", 120L);
        stats.put("suspectCount", 3L);
        stats.put("confirmedCount", 0L);
        stats.put("label", "engineering");
        stats.put("totalUsers", 123L);

        Map<String, Object> filtered = filter.apply(stats);

        assertThat(filtered)
                .containsEntry("activeCount", 120L)
                .containsEntry("suspectCount", "<5")
                .containsEntry("confirmedCount", 0L)
                .containsEntry("label", "engineering")
                .containsEntry("totalUsers", 123L);
    }

    @Test
    void populationsSmallerThanKAreFullyMasked() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeCount", 2);
        stats.put("suspectCount", 1);
        stats.put("totalUsers", 3);
        stats.put("department", "music");
        stats.put("timestamp", "2026-11-06");

        Map<String, Object> masked = filter.apply(stats);

        assertThat(masked).containsOnlyKeys("note", "department", "timestamp", "totalUsers");
        assertThat(masked).containsEntry("totalUsers", "<5").containsEntry("department", "music");
    }

    @Test
    void customKAndMissingFieldsAreHandled() {
        assertThat(filter.apply(Map.of("totalUsers", 8), 10)).containsOnlyKeys("note", "totalUsers")
                .containsEntry("totalUsers", "<10");
        assertThat(filter.apply(Map.of("totalUsers", "n/a", "activeCount", 7), 10)).containsEntry("activeCount", "<10");
        assertThat(filter.apply(null)).isEmpty();
    }
}
