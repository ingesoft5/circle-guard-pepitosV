package com.circleguard.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.circleguard.auth.config.QrProperties;
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
        QrTokenService service = new QrTokenService(new QrProperties(SECRET, 60_000L));

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

    @Test
    void theSameCodeIssuesATokenForWhateverLifetimeItIsGiven() {
        UUID anonymousId = UUID.randomUUID();
        QrTokenService shortLived = new QrTokenService(new QrProperties(SECRET, 30_000L));
        QrTokenService longLived = new QrTokenService(new QrProperties(SECRET, 120_000L));

        assertThat(lifetimeOf(shortLived, anonymousId)).isEqualTo(30_000L);
        assertThat(lifetimeOf(longLived, anonymousId)).isEqualTo(120_000L);
        assertThat(shortLived.expirationMillis()).isEqualTo(30_000L);
        assertThat(longLived.expirationMillis()).isEqualTo(120_000L);
    }

    @Test
    void devEnablesDiagnosticsAndProdKeepsThemOff() {
        assertThat(yaml("src/main/resources/application-dev.yml").getProperty("circleguard.features.qr-diagnostics"))
                .isEqualTo("true");
        assertThat(yaml("src/main/resources/application-dev.yml").getProperty("qr.expiration"))
                .isEqualTo("120000");
        assertThat(yaml("src/main/resources/application-prod.yml").getProperty("circleguard.features.qr-diagnostics"))
                .isEqualTo("false");
        assertThat(yaml("src/main/resources/application-prod.yml").getProperty("qr.expiration"))
                .isEqualTo("30000");
    }

    private static long lifetimeOf(QrTokenService service, UUID anonymousId) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseClaimsJws(service.generateQrToken(anonymousId))
                .getBody();
        return claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
    }

    private static Properties yaml(String path) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(path));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
