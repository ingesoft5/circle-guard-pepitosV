package com.circleguard.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.identity.event.IdentityAccessEvent;
import com.circleguard.identity.service.IdentityVaultService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Mapping, visitor registration and the audit event; HTTP security is in IdentityVaultControllerTest. */
class IdentityVaultControllerUnitTest {

    private IdentityVaultService vaultService;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private IdentityVaultController controller;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        vaultService = mock(IdentityVaultService.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        controller = new IdentityVaultController(vaultService, kafkaTemplate);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private IdentityAccessEvent sentEvent() {
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("audit.identity.accessed"), event.capture());
        return (IdentityAccessEvent) event.getValue();
    }

    @Test
    void mapReturnsTheAnonymousIdOfTheRealIdentity() {
        UUID id = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId("jdoe")).thenReturn(id);

        assertThat(controller.mapIdentity(Map.of("realIdentity", "jdoe")).getBody()).containsEntry("anonymousId", id);
    }

    @Test
    void visitorDetailsAreCombinedIntoOneVaultIdentity() {
        UUID id = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId("VISITOR|ana@mail.com|Ana|Conference")).thenReturn(id);

        var response = controller.registerVisitor(
                Map.of("name", "Ana", "email", "ana@mail.com", "reason_for_visit", "Conference"));

        assertThat(response.getBody()).containsEntry("anonymousId", id);
    }

    @Test
    void successfulLookupIsAuditedWithTheRequestingUser() {
        UUID id = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(id)).thenReturn("jdoe");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("nurse-anon", null, List.of()));

        assertThat(controller.lookupIdentity(id).getBody()).containsEntry("realIdentity", "jdoe");

        IdentityAccessEvent event = sentEvent();
        assertThat(event.getEventType()).isEqualTo("audit.identity.accessed");
        assertThat(event.getSource()).isEqualTo("circleguard-identity-service");
        assertThat(event.getPayload().getAnonymousId()).isEqualTo(id);
        assertThat(event.getPayload().getRequestingUser()).isEqualTo("nurse-anon");
        assertThat(event.getPayload().getAccessStatus()).isEqualTo("SUCCESS");
        assertThat(event.getMetadata().getVersion()).isEqualTo(1);
    }

    @Test
    void notFoundLookupIsAuditedAsAFailure() {
        UUID id = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity not found"));

        assertThatThrownBy(() -> controller.lookupIdentity(id)).isInstanceOf(ResponseStatusException.class);

        IdentityAccessEvent event = sentEvent();
        assertThat(event.getPayload().getAccessStatus()).startsWith("FAILURE_404");
        assertThat(event.getPayload().getRequestingUser()).isEqualTo("system");
    }

    @Test
    void unexpectedLookupErrorIsAuditedAsError() {
        UUID id = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(id)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> controller.lookupIdentity(id)).isInstanceOf(IllegalStateException.class);

        assertThat(sentEvent().getPayload().getAccessStatus()).isEqualTo("ERROR");
    }
}
