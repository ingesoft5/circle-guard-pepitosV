package com.circleguard.dashboard.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PromotionClientTest {

    private PromotionClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new PromotionClient();
        ReflectionTestUtils.setField(client, "promotionServiceUrl", "http://promotion:8088");
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "restTemplate")).build();
    }

    @Test
    void readsCampusAndDepartmentStats() {
        server.expect(requestTo("http://promotion:8088/api/v1/health-status/stats"))
                .andRespond(withSuccess("{\"totalUsers\":500}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://promotion:8088/api/v1/health-status/stats/department/physics"))
                .andRespond(withSuccess("{\"department\":\"physics\",\"totalUsers\":42}", MediaType.APPLICATION_JSON));

        assertThat(client.getHealthStats()).containsEntry("totalUsers", 500);
        assertThat(client.getHealthStatsByDepartment("physics")).containsEntry("totalUsers", 42);
        server.verify();
    }

    @Test
    void unavailablePromotionServiceReturnsAnErrorPayload() {
        server.expect(requestTo("http://promotion:8088/api/v1/health-status/stats")).andRespond(withServerError());
        server.expect(requestTo("http://promotion:8088/api/v1/health-status/stats/department/physics"))
                .andRespond(withServerError());

        assertThat(client.getHealthStats()).containsEntry("error", "Service unavailable");
        assertThat(client.getHealthStatsByDepartment("physics"))
                .containsEntry("error", "Service unavailable")
                .containsEntry("department", "physics");
    }
}
