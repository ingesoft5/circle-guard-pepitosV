package com.circleguard.form.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics of the health questionnaire (HU-39). {@code circleguard.surveys} counts the
 * questionnaires submitted, split by whether the answers reported symptoms; Prometheus exports it
 * as {@code circleguard_surveys_total}.
 */
@Component
public class SurveyMetrics {

    public static final String SURVEYS = "circleguard.surveys";

    private final Counter withSymptoms;
    private final Counter withoutSymptoms;

    public SurveyMetrics(MeterRegistry registry) {
        this.withSymptoms = counter(registry, "true");
        this.withoutSymptoms = counter(registry, "false");
    }

    public void recordSubmitted(boolean hasSymptoms) {
        (hasSymptoms ? withSymptoms : withoutSymptoms).increment();
    }

    private static Counter counter(MeterRegistry registry, String symptoms) {
        return Counter.builder(SURVEYS)
                .description("Health questionnaires submitted, by whether they reported symptoms")
                .tag("symptoms", symptoms)
                .register(registry);
    }
}
