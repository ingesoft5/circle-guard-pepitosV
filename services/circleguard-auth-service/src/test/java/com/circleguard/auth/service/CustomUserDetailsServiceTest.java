package com.circleguard.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.model.Permission;
import com.circleguard.auth.model.Role;
import com.circleguard.auth.repository.LocalUserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class CustomUserDetailsServiceTest {

    private LocalUserRepository repository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        repository = mock(LocalUserRepository.class);
        service = new CustomUserDetailsService(repository);
    }

    private LocalUser user(boolean active) {
        Permission lookup = Permission.builder().name("identity:lookup").build();
        Permission report = Permission.builder().name("health:report").build();
        Role role = Role.builder().name("HEALTH_CENTER").permissions(Set.of(lookup, report)).build();
        return LocalUser.builder()
                .username("nurse")
                .password("$2a$10$hash")
                .isActive(active)
                .roles(Set.of(role))
                .build();
    }

    @Test
    void activeUserGetsItsRolesWithPrefixAndItsPermissions() {
        when(repository.findByUsername("nurse")).thenReturn(Optional.of(user(true)));

        UserDetails details = service.loadUserByUsername("nurse");

        assertThat(details.getUsername()).isEqualTo("nurse");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_HEALTH_CENTER", "identity:lookup", "health:report");
    }

    @Test
    void unknownUserIsRejected() {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void disabledUserIsRejected() {
        when(repository.findByUsername("nurse")).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> service.loadUserByUsername("nurse"))
                .isInstanceOf(DisabledException.class);
    }
}
