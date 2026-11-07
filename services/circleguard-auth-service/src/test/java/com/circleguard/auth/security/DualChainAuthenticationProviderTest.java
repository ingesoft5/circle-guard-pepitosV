package com.circleguard.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

class DualChainAuthenticationProviderTest {

    private LdapAuthenticationProvider ldap;
    private DaoAuthenticationProvider local;
    private DualChainAuthenticationProvider provider;
    private final Authentication request = new UsernamePasswordAuthenticationToken("jdoe", "secret");

    @BeforeEach
    void setUp() {
        ldap = mock(LdapAuthenticationProvider.class);
        local = mock(DaoAuthenticationProvider.class);
        provider = new DualChainAuthenticationProvider(ldap, local);
    }

    @Test
    void ldapIsTriedFirst() {
        Authentication fromLdap = mock(Authentication.class);
        when(ldap.authenticate(request)).thenReturn(fromLdap);

        assertThat(provider.authenticate(request)).isSameAs(fromLdap);
        verify(local, never()).authenticate(request);
    }

    @Test
    void fallsBackToTheLocalDatabaseWhenLdapRejects() {
        Authentication fromDb = mock(Authentication.class);
        when(ldap.authenticate(request)).thenThrow(new BadCredentialsException("not in LDAP"));
        when(local.authenticate(request)).thenReturn(fromDb);

        assertThat(provider.authenticate(request)).isSameAs(fromDb);
    }

    @Test
    void failsWhenBothChainsReject() {
        when(ldap.authenticate(request)).thenThrow(new BadCredentialsException("not in LDAP"));
        when(local.authenticate(request)).thenThrow(new BadCredentialsException("bad password"));

        assertThatThrownBy(() -> provider.authenticate(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("bad password");
    }

    @Test
    void supportsOnlyUsernamePasswordTokens() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(AnonymousAuthenticationToken.class)).isFalse();
    }
}
