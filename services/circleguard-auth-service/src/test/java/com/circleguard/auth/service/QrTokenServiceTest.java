package com.circleguard.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

class QrTokenServiceTest {

    private static final String SECRET = "test-qr-secret-key-with-at-least-32-bytes";

    @Test
    void tokenCarriesSubjectAndConfiguredLifetime() {
        UUID anonymousId = UUID.randomUUID();
        QrTokenService service = new QrTokenService(SECRET, 60_000L);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseClaimsJws(service.generateQrToken(anonymousId))
                .getBody();

        assertThat(claims.getSubject()).isEqualTo(anonymousId.toString());
        long lifetimeMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(lifetimeMillis).isEqualTo(60_000L);
    }

    @Test
    void deployedConfigurationKeepsTokensValidForTheAdvertisedSixtySeconds() {
        // The test classpath shadows application.yml, so the main file is read from disk.
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(Long.parseLong(properties.getProperty("qr.expiration"))).isEqualTo(60_000L);
    }
}
