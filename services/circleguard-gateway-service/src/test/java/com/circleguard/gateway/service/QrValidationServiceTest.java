package com.circleguard.gateway.service;

import com.circleguard.gateway.metrics.CheckInMetrics;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class QrValidationServiceTest {

    private QrValidationService service;
    private HealthStatusLookup healthStatusLookup;
    private SimpleMeterRegistry meters;
    private final String secret = "my-super-secret-test-key-32-chars-long";

    @BeforeEach
    void setUp() {
        healthStatusLookup = Mockito.mock(HealthStatusLookup.class);
        meters = new SimpleMeterRegistry();
        service = new QrValidationService(healthStatusLookup, new CheckInMetrics(meters));
        ReflectionTestUtils.setField(service, "qrSecret", secret);
    }

    @Test
    void shouldValidateCorrectTokenAndAllowAccess() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(healthStatusLookup.currentStatus(anonymousId)).thenReturn("CLEAR");

        QrValidationService.ValidationResult result = service.validateToken(token);
        
        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
    }

    @Test
    void shouldDenyAccessForContagiedUser() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(healthStatusLookup.currentStatus(anonymousId)).thenReturn("CONTAGIED");

        QrValidationService.ValidationResult result = service.validateToken(token);
        
        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    @Test
    void shouldDenyAccessWhenHealthStatusIsUnavailable() {
        String anonymousId = UUID.randomUUID().toString();
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        String token = Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        Mockito.when(healthStatusLookup.currentStatus(anonymousId)).thenReturn(HealthStatusLookup.UNAVAILABLE);

        QrValidationService.ValidationResult result = service.validateToken(token);

        assertFalse(result.valid());
        assertEquals("RED", result.status());
        assertEquals("Access Denied: Health status unavailable", result.message());
    }

    private String tokenFor(String anonymousId) {
        Key key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .setSubject(anonymousId)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private double checkIns(String result) {
        return meters.get("circleguard.checkins").tag("result", result).counter().count();
    }

    @Test
    void aGrantedCheckInIsCounted() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(healthStatusLookup.currentStatus(anonymousId)).thenReturn("CLEAR");

        service.validateToken(tokenFor(anonymousId));

        assertEquals(1.0, checkIns("granted"));
        assertEquals(0.0, checkIns("denied_health_risk"));
        assertEquals(0.0, checkIns("denied_status_unavailable"));
        assertEquals(0.0, checkIns("invalid_token"));
    }

    @Test
    void aHealthRiskDenialIsCounted() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(healthStatusLookup.currentStatus(anonymousId)).thenReturn("CONTAGIED");

        service.validateToken(tokenFor(anonymousId));

        assertEquals(1.0, checkIns("denied_health_risk"));
        assertEquals(0.0, checkIns("granted"));
    }

    @Test
    void anUnavailableStatusDenialIsCounted() {
        String anonymousId = UUID.randomUUID().toString();
        Mockito.when(healthStatusLookup.currentStatus(anonymousId)).thenReturn(HealthStatusLookup.UNAVAILABLE);

        service.validateToken(tokenFor(anonymousId));

        assertEquals(1.0, checkIns("denied_status_unavailable"));
        assertEquals(0.0, checkIns("granted"));
    }

    @Test
    void anInvalidTokenIsCounted() {
        service.validateToken("not-a-jwt");

        assertEquals(1.0, checkIns("invalid_token"));
        assertEquals(0.0, checkIns("granted"));
    }
}
