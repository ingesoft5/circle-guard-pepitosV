package com.circleguard.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class JwtTokenServiceTest {

    private static final String SECRET = "unit-test-jwt-secret-with-at-least-32-bytes";

    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Test
    void tokenUsesTheAnonymousIdAsSubjectAndCarriesThePermissions() {
        JwtTokenService service = new JwtTokenService(SECRET, 3_600_000L);
        UUID anonymousId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("jdoe", null, List.of(
                new SimpleGrantedAuthority("ROLE_STUDENT"),
                new SimpleGrantedAuthority("identity:lookup")));

        Claims claims = parse(service.generateToken(anonymousId, auth));

        assertThat(claims.getSubject()).isEqualTo(anonymousId.toString());
        assertThat(claims.get("permissions", List.class)).containsExactly("ROLE_STUDENT", "identity:lookup");
    }

    @Test
    void tokenExpiresAfterTheConfiguredLifetime() {
        JwtTokenService service = new JwtTokenService(SECRET, 120_000L);
        var auth = new UsernamePasswordAuthenticationToken("jdoe", null, List.of());

        Claims claims = parse(service.generateToken(UUID.randomUUID(), auth));

        long lifetime = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(lifetime).isBetween(119_000L, 121_000L);
        assertThat(claims.get("permissions", List.class)).isEmpty();
    }
}
