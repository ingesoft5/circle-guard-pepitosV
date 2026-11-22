package com.circleguard.gateway.service;

import com.circleguard.gateway.metrics.CheckInMetrics;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.Key;

@Service
@RequiredArgsConstructor
public class QrValidationService {
    private final HealthStatusLookup healthStatusLookup;
    private final CheckInMetrics checkInMetrics;

    @Value("${qr.secret}")
    private String qrSecret;

    public ValidationResult validateToken(String token) {
        try {
            Key key = Keys.hmacShaKeyFor(qrSecret.getBytes());
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String anonymousId = claims.getSubject();
            String status = healthStatusLookup.currentStatus(anonymousId);

            if (HealthStatusLookup.UNAVAILABLE.equals(status)) {
                checkInMetrics.record(CheckInMetrics.DENIED_STATUS_UNAVAILABLE);
                return new ValidationResult(false, "RED", "Access Denied: Health status unavailable");
            }

            if ("CONTAGIED".equals(status) || "POTENTIAL".equals(status)) {
                checkInMetrics.record(CheckInMetrics.DENIED_HEALTH_RISK);
                return new ValidationResult(false, "RED", "Access Denied: Health Risk Detected");
            }

            checkInMetrics.record(CheckInMetrics.GRANTED);
            return new ValidationResult(true, "GREEN", "Welcome to Campus");
            
        } catch (Exception e) {
            checkInMetrics.record(CheckInMetrics.INVALID_TOKEN);
            return new ValidationResult(false, "RED", "Invalid or Expired Token");
        }
    }

    public record ValidationResult(boolean valid, String status, String message) {}
}
