package com.circleguard.auth.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "circleguard.features")
public class FeatureProperties {
    /** Authenticated view of the configured QR lifetime. On in dev, off in prod. */
    private boolean qrDiagnostics = false;
}
