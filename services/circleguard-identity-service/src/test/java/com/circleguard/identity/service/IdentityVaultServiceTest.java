package com.circleguard.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class IdentityVaultServiceTest {

    private IdentityMappingRepository repository;
    private IdentityVaultService service;

    @BeforeEach
    void setUp() {
        repository = mock(IdentityMappingRepository.class);
        service = new IdentityVaultService(repository);
        ReflectionTestUtils.setField(service, "hashSalt", "unit-test-salt");
    }

    @Test
    void existingIdentityReusesItsAnonymousId() {
        UUID existing = UUID.randomUUID();
        when(repository.findByIdentityHash(anyString()))
                .thenReturn(Optional.of(IdentityMapping.builder().anonymousId(existing).build()));

        assertThat(service.getOrCreateAnonymousId("jdoe")).isEqualTo(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void newIdentityIsStoredWithAHashAndARandomSalt() {
        UUID created = UUID.randomUUID();
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdentityMapping.class))).thenAnswer(invocation -> {
            IdentityMapping mapping = invocation.getArgument(0);
            mapping.setAnonymousId(created);
            return mapping;
        });

        assertThat(service.getOrCreateAnonymousId("jdoe")).isEqualTo(created);

        ArgumentCaptor<IdentityMapping> saved = ArgumentCaptor.forClass(IdentityMapping.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRealIdentity()).isEqualTo("jdoe");
        assertThat(saved.getValue().getIdentityHash()).matches("[0-9a-f]{64}").doesNotContain("jdoe");
        assertThat(saved.getValue().getSalt()).isNotBlank();
    }

    @Test
    void theSameIdentityAlwaysProducesTheSameHash() {
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(IdentityMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);

        service.getOrCreateAnonymousId("jdoe");
        service.getOrCreateAnonymousId("jdoe");
        service.getOrCreateAnonymousId("asmith");

        verify(repository, org.mockito.Mockito.times(3)).findByIdentityHash(hashes.capture());
        assertThat(hashes.getAllValues().get(0)).isEqualTo(hashes.getAllValues().get(1));
        assertThat(hashes.getAllValues().get(2)).isNotEqualTo(hashes.getAllValues().get(0));
    }

    @Test
    void resolvesTheRealIdentityOfAKnownAnonymousId() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(IdentityMapping.builder().realIdentity("jdoe").build()));

        assertThat(service.resolveRealIdentity(id)).isEqualTo("jdoe");
    }

    @Test
    void unknownAnonymousIdIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveRealIdentity(id))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
