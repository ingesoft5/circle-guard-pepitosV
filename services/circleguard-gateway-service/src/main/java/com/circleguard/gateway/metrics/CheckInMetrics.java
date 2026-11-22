package com.circleguard.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Business metrics of the campus gate (HU-39). {@code circleguard.checkins} counts every QR
 * validation by outcome; Prometheus exports it as {@code circleguard_checkins_total}.
 */
@Component
public class CheckInMetrics {

    public static final String CHECKINS = "circleguard.checkins";

    public static final String GRANTED = "granted";
    public static final String DENIED_HEALTH_RISK = "denied_health_risk";
    public static final String DENIED_STATUS_UNAVAILABLE = "denied_status_unavailable";
    public static final String INVALID_TOKEN = "invalid_token";

    private final Map<String, Counter> counters;

    public CheckInMetrics(MeterRegistry registry) {
        this.counters = List.of(GRANTED, DENIED_HEALTH_RISK, DENIED_STATUS_UNAVAILABLE, INVALID_TOKEN).stream()
                .collect(Collectors.toUnmodifiableMap(
                        result -> result,
                        result -> Counter.builder(CHECKINS)
                                .description("Gate check-ins by outcome")
                                .tag("result", result)
                                .register(registry)));
    }

    public void record(String result) {
        counters.get(result).increment();
    }
}
