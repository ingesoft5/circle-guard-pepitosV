package com.circleguard.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest(properties = {
        "resilience4j.circuitbreaker.instances.redisHealth.slidingWindowSize=4",
        "resilience4j.circuitbreaker.instances.redisHealth.minimumNumberOfCalls=4",
        "resilience4j.circuitbreaker.instances.redisHealth.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.redisHealth.waitDurationInOpenState=200ms",
        "resilience4j.circuitbreaker.instances.redisHealth.permittedNumberOfCallsInHalfOpenState=1",
        "resilience4j.circuitbreaker.instances.redisHealth.automaticTransitionFromOpenToHalfOpenEnabled=true",
        "resilience4j.retry.instances.redisHealth.maxAttempts=2",
        "resilience4j.retry.instances.redisHealth.waitDuration=1ms"
})
class RedisHealthCircuitBreakerTest {

    private static final String VISITOR = "visitor-1";

    @Autowired
    private HealthStatusLookup lookup;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @Autowired
    private MeterRegistry meters;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);

    @BeforeEach
    void redisAnswersThroughTheStub() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        circuitBreakers.circuitBreaker(HealthStatusLookup.BREAKER).reset();
    }

    @Test
    void aSingleBlipIsRetriedBeforeTheCallSucceeds() {
        when(values.get("user:status:" + VISITOR))
                .thenThrow(new RedisConnectionFailureException("blip"))
                .thenReturn("CLEAR");

        assertThat(lookup.currentStatus(VISITOR)).isEqualTo("CLEAR");
        verify(values, times(2)).get("user:status:" + VISITOR);
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void repeatedFailuresOpenTheCircuitAndTheNextCallSkipsRedis() {
        when(values.get("user:status:" + VISITOR)).thenThrow(new RedisConnectionFailureException("down"));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThat(lookup.currentStatus(VISITOR)).isEqualTo(HealthStatusLookup.UNAVAILABLE);
        }

        verify(values, times(8)).get("user:status:" + VISITOR);
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(stateGauge("open")).isEqualTo(1.0);

        assertThat(lookup.currentStatus(VISITOR)).isEqualTo(HealthStatusLookup.UNAVAILABLE);
        verify(values, times(8)).get("user:status:" + VISITOR);
    }

    @Test
    void theCircuitClosesAgainAfterRedisRecovers() throws Exception {
        when(values.get("user:status:" + VISITOR)).thenThrow(new RedisConnectionFailureException("down"));
        for (int attempt = 0; attempt < 4; attempt++) {
            lookup.currentStatus(VISITOR);
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(250);
        org.mockito.Mockito.reset(values);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("user:status:" + VISITOR)).thenReturn("CLEAR");

        assertThat(lookup.currentStatus(VISITOR)).isEqualTo("CLEAR");
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(stateGauge("closed")).isEqualTo(1.0);
    }

    private CircuitBreaker breaker() {
        return circuitBreakers.circuitBreaker(HealthStatusLookup.BREAKER);
    }

    private double stateGauge(String state) {
        Gauge gauge = meters.find("resilience4j.circuitbreaker.state")
                .tag("name", HealthStatusLookup.BREAKER)
                .tag("state", state)
                .gauge();
        assertThat(gauge).as("circuit breaker %s metric", state).isNotNull();
        return gauge.value();
    }
}
