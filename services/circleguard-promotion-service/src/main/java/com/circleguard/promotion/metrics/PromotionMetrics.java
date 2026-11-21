package com.circleguard.promotion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Business metrics of the promotion engine (HU-39).
 *
 * <ul>
 *   <li>{@code circleguard.promotions}: users whose status the engine raised, by new status and by
 *       origin ({@code source} is the user whose status changed, {@code contact} a user reached by
 *       the cascade). Exported to Prometheus as {@code circleguard_promotions_total}.</li>
 *   <li>{@code circleguard.containment.duration}: time one status change takes, from the start of
 *       the update to the last Kafka event (Cypher cascade, Redis refresh and broadcasts). The
 *       buckets include 1 s, the NFR-1 target. Exported as
 *       {@code circleguard_containment_duration_seconds}.</li>
 * </ul>
 *
 * Status values reach this class from the API, so any value outside the known statuses is
 * reported as {@code OTHER}; that keeps the number of time series bounded.
 */
@Component
public class PromotionMetrics {

    public static final String PROMOTIONS = "circleguard.promotions";
    public static final String CONTAINMENT = "circleguard.containment.duration";

    public static final String ORIGIN_SOURCE = "source";
    public static final String ORIGIN_CONTACT = "contact";

    public static final String SUSPECT = "SUSPECT";
    public static final String PROBABLE = "PROBABLE";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String RECOVERED = "RECOVERED";
    private static final String ACTIVE = "ACTIVE";
    private static final String OTHER = "OTHER";

    private static final List<String> PROMOTED = List.of(SUSPECT, PROBABLE, CONFIRMED, RECOVERED);
    private static final Set<String> KNOWN = Set.of(ACTIVE, SUSPECT, PROBABLE, CONFIRMED, RECOVERED);
    private static final Duration[] CONTAINMENT_BUCKETS = {
        Duration.ofMillis(250),
        Duration.ofMillis(500),
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        Duration.ofSeconds(5)
    };

    private final MeterRegistry registry;

    public PromotionMetrics(MeterRegistry registry) {
        this.registry = registry;
        // Registered up front so the series exist (at zero) before the first promotion.
        for (String status : PROMOTED) {
            counter(status, ORIGIN_SOURCE);
            counter(status, ORIGIN_CONTACT);
        }
        for (String status : KNOWN) {
            containmentTimer(status);
        }
    }

    /** Counts the user whose status changed and each contact the cascade moved. */
    public void recordPromotions(String status, List<? extends Map<String, ?>> affectedContacts) {
        recordPromotion(status, ORIGIN_SOURCE);
        if (affectedContacts == null) {
            return;
        }
        for (Map<String, ?> contact : affectedContacts) {
            if (contact != null && contact.get("id") != null) {
                recordPromotion(contact.get("status"), ORIGIN_CONTACT);
            }
        }
    }

    /** Counts one promotion; ACTIVE and unknown statuses are not promotions. */
    public void recordPromotion(Object status, String origin) {
        String label = label(status);
        if (PROMOTED.contains(label)) {
            counter(label, origin).increment();
        }
    }

    public Timer.Sample startContainment() {
        return Timer.start(registry);
    }

    public void stopContainment(Timer.Sample sample, String status) {
        sample.stop(containmentTimer(label(status)));
    }

    private static String label(Object status) {
        return status instanceof String && KNOWN.contains(status) ? (String) status : OTHER;
    }

    private Counter counter(String status, String origin) {
        return Counter.builder(PROMOTIONS)
                .description("Users whose health status the promotion engine raised")
                .tag("status", status)
                .tag("origin", origin)
                .register(registry);
    }

    private Timer containmentTimer(String status) {
        return Timer.builder(CONTAINMENT)
                .description("Time to apply a status change: Neo4j cascade, Redis refresh and Kafka events")
                .tag("status", status)
                .serviceLevelObjectives(CONTAINMENT_BUCKETS)
                .register(registry);
    }
}
