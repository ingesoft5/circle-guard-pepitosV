package com.circleguard.auth.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "qr")
public class QrProperties {
    private String secret;
    /** Lifetime of a campus QR, in milliseconds. Overridable without rebuilding the image. */
    private long expiration = 60_000L;
}
