package com.circleguard.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.service.JwtTokenService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

/** Error paths and the visitor handoff; the happy path over HTTP is in LoginControllerTest. */
class LoginControllerUnitTest {

    private AuthenticationManager authManager;
    private JwtTokenService jwtService;
    private IdentityClient identityClient;
    private LoginController controller;

    @BeforeEach
    void setUp() {
        authManager = mock(AuthenticationManager.class);
        jwtService = mock(JwtTokenService.class);
        identityClient = mock(IdentityClient.class);
        controller = new LoginController(authManager, jwtService, identityClient);
    }

    @Test
    void wrongCredentialsReturn401() {
        when(authManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        ResponseEntity<Map<String, String>> response = controller.login(Map.of("username", "jdoe", "password", "x"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsEntry("message", "Invalid username or password");
    }

    @Test
    void identityServiceFailureReturns500() {
        when(authManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(identityClient.getAnonymousId("jdoe")).thenThrow(new IllegalStateException("identity down"));

        ResponseEntity<Map<String, String>> response = controller.login(Map.of("username", "jdoe", "password", "x"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().get("message")).contains("identity down");
    }

    @Test
    void visitorHandoffIssuesAVisitorToken() {
        UUID anonymousId = UUID.randomUUID();
        when(jwtService.generateToken(eq(anonymousId), any())).thenReturn("visitor-jwt");

        ResponseEntity<Map<String, String>> response =
                controller.generateVisitorHandoff(Map.of("anonymousId", anonymousId.toString()));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody())
                .containsEntry("token", "visitor-jwt")
                .containsEntry("handoffPayload", "HANDOFF_TOKEN:" + anonymousId + ":visitor-jwt");

        ArgumentCaptor<Authentication> auth = ArgumentCaptor.forClass(Authentication.class);
        org.mockito.Mockito.verify(jwtService).generateToken(eq(anonymousId), auth.capture());
        assertThat(auth.getValue().getAuthorities()).extracting(Object::toString).containsExactly("VISITOR");
    }

    @Test
    void visitorHandoffWithoutAnonymousIdIsABadRequest() {
        assertThat(controller.generateVisitorHandoff(Map.of()).getStatusCode().value()).isEqualTo(400);
    }
}
