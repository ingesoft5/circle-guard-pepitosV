package com.circleguard.auth.security;

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

    private String token(String subject, Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    private Authentication run(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validTokenAuthenticatesTheSubjectWithItsPermissions() throws Exception {
        Authentication auth = run("Bearer " + token("anon-1", Map.of("permissions", List.of("identity:lookup"))));

        assertThat(auth.getName()).isEqualTo("anon-1");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("identity:lookup");
    }

    @Test
    void tokenWithoutPermissionsAuthenticatesWithNoAuthorities() throws Exception {
        Authentication auth = run("Bearer " + token("anon-2", Map.of()));

        assertThat(auth.getName()).isEqualTo("anon-2");
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void invalidTokenLeavesTheRequestUnauthenticated() throws Exception {
        assertThat(run("Bearer not-a-jwt")).isNull();
    }

    @Test
    void requestsWithoutBearerTokenPassThrough() throws Exception {
        assertThat(run(null)).isNull();
        assertThat(run("Basic dXNlcjpwYXNz")).isNull();
    }
}
