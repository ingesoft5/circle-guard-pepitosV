package com.circleguard.form.config;

import java.time.Duration;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@NoArgsConstructor
@ConfigurationProperties(prefix = "outbox")
public class OutboxProperties {

    /** Published events older than this are deleted by the cleanup task. */
    private Duration retention = Duration.ofDays(7);

    private Relay relay = new Relay();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Relay {
        /** Polling delay for the scheduled relay. */
        private Duration delay = Duration.ofSeconds(1);
        private int batchSize = 50;
        private Duration sendTimeout = Duration.ofSeconds(10);
    }
}
