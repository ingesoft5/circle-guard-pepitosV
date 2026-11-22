package com.circleguard.gateway.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Reads the visitor health status from Redis. A short retry absorbs a blip.
 * If Redis stays down, the circuit opens and the gate fails closed without
 * waiting for another timeout.
 */
@Component
public class HealthStatusLookup {

    static final String UNAVAILABLE = "UNAVAILABLE";
    static final String BREAKER = "redisHealth";
    private static final String STATUS_KEY_PREFIX = "user:status:";

    private final StringRedisTemplate redisTemplate;

    public HealthStatusLookup(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @CircuitBreaker(name = BREAKER, fallbackMethod = "statusUnavailable")
    @Retry(name = BREAKER)
    public String currentStatus(String anonymousId) {
        return redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + anonymousId);
    }

    @SuppressWarnings("unused")
    private String statusUnavailable(String anonymousId, Throwable failure) {
        return UNAVAILABLE;
    }
}
