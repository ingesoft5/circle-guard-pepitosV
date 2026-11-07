package com.circleguard.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleguard.dashboard.service.AnalyticsService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnalyticsControllerUnitTest {

    @Test
    void summaryDepartmentAndTimeSeriesDelegateToTheService() {
        AnalyticsService service = mock(AnalyticsService.class);
        AnalyticsController controller = new AnalyticsController(service);
        Map<String, Object> summary = Map.of("totalUsers", 500L);
        Map<String, Object> department = Map.of("department", "physics");
        List<Map<String, Object>> series = List.of(Map.of("status", "ACTIVE"));
        when(service.getCampusSummary()).thenReturn(summary);
        when(service.getDepartmentStats("physics")).thenReturn(department);
        when(service.getTimeSeries("daily", 7)).thenReturn(series);

        assertThat(controller.getSummary().getBody()).isEqualTo(summary);
        assertThat(controller.getDepartmentStats("physics").getBody()).isEqualTo(department);
        assertThat(controller.getTimeSeries("daily", 7).getBody()).isEqualTo(series);
    }
}
