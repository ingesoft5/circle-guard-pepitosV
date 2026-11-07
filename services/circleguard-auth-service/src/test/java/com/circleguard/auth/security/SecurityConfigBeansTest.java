package com.circleguard.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.circleguard.auth.service.CustomUserDetailsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

class SecurityConfigBeansTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void corsAllowsOnlyTheConfiguredOrigins() {
        var source = (UrlBasedCorsConfigurationSource) config.corsConfigurationSource(List.of("https://web.example.edu"));

        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/auth/login"));

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("https://web.example.edu");
        assertThat(cors.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void ldapContextUsesTheConfiguredServer() {
        LdapContextSource source = config.contextSource(
                "ldap://ldap.example.edu:389", "dc=circleguard,dc=edu", "cn=admin,dc=circleguard,dc=edu", "secret");

        assertThat(source.getUrls()).containsExactly("ldap://ldap.example.edu:389");
        assertThat(source.getBaseLdapPathAsString()).isEqualTo("dc=circleguard,dc=edu");
        assertThat(source.getUserDn()).isEqualTo("cn=admin,dc=circleguard,dc=edu");
    }

    @Test
    void authenticationProvidersAreBuilt() {
        LdapContextSource source = config.contextSource(
                "ldap://localhost:389", "dc=circleguard,dc=edu", "cn=admin,dc=circleguard,dc=edu", "secret");

        assertThat(config.ldapAuthenticationProvider(source)).isNotNull();
        assertThat(config.daoAuthenticationProvider(mock(CustomUserDetailsService.class), config.passwordEncoder()))
                .isNotNull();
    }

    @Test
    void passwordsAreHashedWithBcrypt() {
        PasswordEncoder encoder = config.passwordEncoder();

        String hash = encoder.encode("password123");

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches("password123", hash)).isTrue();
    }
}
