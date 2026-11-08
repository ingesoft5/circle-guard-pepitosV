package com.circleguard.gateway.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * gateway-service accepts a current signed QR and rejects an expired one, with status stored in Redis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class QrRedisIntegrationTest {

    private static final String QR_SECRET = "my-qr-secret-key-for-dev-1234567890";

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.2.1").withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String anonymousId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void rememberTheVisitor() {
        anonymousId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("user:status:" + anonymousId, "ACTIVE");
    }

    @Test
    void currentQrIsAcceptedAndExpiredQrIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/gate/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("GREEN"));

        mockMvc.perform(post("/api/v1/gate/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("RED"));
    }

    private String body(boolean expired) {
        return "{\"token\":\"" + signedToken(expired) + "\"}";
    }

    private String signedToken(boolean expired) {
        Key key = Keys.hmacShaKeyFor(QR_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        Date issuedAt = new Date(expired ? now - 120_000 : now);
        Date expiration = new Date(expired ? now - 60_000 : now + 120_000);
        return Jwts.builder()
                .setSubject(anonymousId)
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
