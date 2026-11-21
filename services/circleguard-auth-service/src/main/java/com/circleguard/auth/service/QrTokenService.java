package com.circleguard.auth.service;

import com.circleguard.auth.config.QrProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Service
public class QrTokenService {
    private final Key key;
    private final long expiration;

    public QrTokenService(QrProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes());
        this.expiration = properties.getExpiration();
    }

    public long expirationMillis() {
        return expiration;
    }

    public String generateQrToken(UUID anonymousId) {
        return Jwts.builder()
                .setSubject(anonymousId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
