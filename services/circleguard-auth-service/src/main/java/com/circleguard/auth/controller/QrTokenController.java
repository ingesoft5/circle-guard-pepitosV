package com.circleguard.auth.controller;

import com.circleguard.auth.config.FeatureProperties;
import com.circleguard.auth.service.QrTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/qr")
@RequiredArgsConstructor
public class QrTokenController {
    private final QrTokenService qrService;
    private final FeatureProperties features;

    /**
     * Generates a short-lived QR token for campus entry.
     * The UID is extracted from the JWT authentication context.
     */
    @GetMapping("/generate")
    public ResponseEntity<Map<String, String>> generateToken(Authentication auth) {
        // In a real app, the anonymousId is stored in the JWT principal/claims
        UUID anonymousId = UUID.fromString(auth.getName());
        String token = qrService.generateQrToken(anonymousId);
        
        return ResponseEntity.ok(Map.of(
            "qrToken", token,
            "expiresIn", Long.toString(qrService.expirationMillis() / 1000)
        ));
    }

    /**
     * Shows the QR lifetime currently in effect. Available only when the
     * qr-diagnostics feature is on, so production does not expose it.
     */
    @GetMapping("/diagnostics")
    public ResponseEntity<Map<String, String>> diagnostics() {
        if (!features.isQrDiagnostics()) {
            return ResponseEntity.notFound().build();
        }
        long expirationMillis = qrService.expirationMillis();
        return ResponseEntity.ok(Map.of(
            "expirationMillis", Long.toString(expirationMillis),
            "expiresIn", Long.toString(expirationMillis / 1000)
        ));
    }
}
