package com.circleguard.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.repository.LocalUserRepository;
import com.circleguard.auth.service.QrTokenService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class QrAndUserControllerTest {

    @Test
    void qrTokenIsGeneratedForTheAuthenticatedAnonymousId() {
        QrTokenService qrService = mock(QrTokenService.class);
        UUID anonymousId = UUID.randomUUID();
        when(qrService.generateQrToken(anonymousId)).thenReturn("qr-jwt");
        var controller = new QrTokenController(qrService);

        var response = controller.generateToken(
                new UsernamePasswordAuthenticationToken(anonymousId.toString(), null, List.of()));

        assertThat(response.getBody()).containsEntry("qrToken", "qr-jwt").containsEntry("expiresIn", "60");
    }

    @Test
    void usersWithAPermissionAreListedWithTheirEmail() {
        LocalUserRepository repository = mock(LocalUserRepository.class);
        when(repository.findUsersByPermissionName("alert:receive_priority")).thenReturn(List.of(
                LocalUser.builder().username("admin").email("admin@circleguard.edu").build(),
                LocalUser.builder().username("noemail").build()));
        var controller = new UserController(repository);

        List<Map<String, String>> users = controller.getUsersByPermission("alert:receive_priority").getBody();

        assertThat(users).containsExactly(
                Map.of("username", "admin", "email", "admin@circleguard.edu"),
                Map.of("username", "noemail", "email", ""));
    }
}
