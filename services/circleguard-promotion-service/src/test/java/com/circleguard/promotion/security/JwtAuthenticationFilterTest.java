package com.circleguard.promotion.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "unit-test-jwt-secret-with-at-least-32-bytes";
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(SECRET);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private String token(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject("anon-1")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    private Authentication run(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validTokenAuthenticatesWithItsPermissions() throws Exception {
        Authentication auth = run("Bearer " + token(Map.of("permissions", List.of("ROLE_HEALTH_CENTER"))));

        assertThat(auth.getName()).isEqualTo("anon-1");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_HEALTH_CENTER");
    }

    @Test
    void tokensWithoutPermissionsOrInvalidOnesAreIgnored() throws Exception {
        assertThat(run("Bearer " + token(Map.of()))).isNull();
        assertThat(run("Bearer not-a-jwt")).isNull();
        assertThat(run(null)).isNull();
        assertThat(run("Basic dXNlcjpwYXNz")).isNull();
    }
}
