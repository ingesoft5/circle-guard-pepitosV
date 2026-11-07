package com.circleguard.promotion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.service.AutoCircleService;
import com.circleguard.promotion.service.CircleService;
import com.circleguard.promotion.service.HealthStatusService;
import com.circleguard.promotion.service.LocationResolutionService;
import com.circleguard.promotion.service.MacSessionRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphControllersUnitTest {

    @Test
    void circleEndpointsDelegateToTheCircleService() {
        CircleService service = mock(CircleService.class);
        CircleController controller = new CircleController(service);
        CircleNode circle = CircleNode.builder().id(7L).name("Lab").build();
        CircleController.CircleCreateRequest request = new CircleController.CircleCreateRequest();
        request.setName("Lab");
        request.setLocationId("ap-1");
        when(service.createCircle("Lab", "ap-1")).thenReturn(circle);
        when(service.joinCircle("u1", "MESH-ABCD")).thenReturn(circle);
        when(service.addMember(7L, "u2")).thenReturn(circle);
        when(service.getUserCircles("u1")).thenReturn(List.of(circle));

        assertThat(controller.createCircle(request).getBody()).isSameAs(circle);
        assertThat(controller.joinCircle("MESH-ABCD", "u1").getBody()).isSameAs(circle);
        assertThat(controller.addMember(7L, "u2").getBody()).isSameAs(circle);
        assertThat(controller.getUserCircles("u1").getBody()).containsExactly(circle);
        assertThat(controller.toggleValidity(7L).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.forceFence(7L).getStatusCode().value()).isEqualTo(200);
        verify(service).toggleCircleValidity(7L);
        verify(service).forceFenceCircle(7L);
    }

    @Test
    void encounterEndpointsRecordAndEvaluateEncounters() {
        UserNodeRepository repository = mock(UserNodeRepository.class);
        AutoCircleService autoCircles = mock(AutoCircleService.class);
        EncounterController controller = new EncounterController(repository, autoCircles);
        EncounterController.EncounterRequest ble = new EncounterController.EncounterRequest();
        ble.setSourceId("a");
        ble.setTargetId("b");
        EncounterController.EncounterRequest wifi = new EncounterController.EncounterRequest();
        wifi.setSourceId("a");
        wifi.setTargetId("c");
        wifi.setLocationId("ap-1");

        controller.reportEncounter(ble);
        controller.reportEncounter(wifi);
        controller.toggleValidity(11L);
        controller.forceFence(12L);

        verify(repository).recordEncounter(eq("a"), eq("b"), anyLong(), eq("mobile_ble"));
        verify(repository).recordEncounter(eq("a"), eq("c"), anyLong(), eq("ap-1"));
        verify(autoCircles).evaluateEncounter("a", "b");
        verify(repository).toggleEncounterValidity(11L);
        verify(repository).forceEncounterFence(12L);
    }

    @Test
    void meshStatsCountConfirmedAndUnconfirmedConnections() {
        UserNodeRepository repository = mock(UserNodeRepository.class);
        when(repository.getConfirmedConnectionCount("u1")).thenReturn(3L);
        when(repository.getUnconfirmedConnectionCount("u1")).thenReturn(5L);

        MeshController.MeshStatsResponse stats = new MeshController(repository).getMeshStats("u1").getBody();

        assertThat(stats.getConfirmedCount()).isEqualTo(3);
        assertThat(stats.getUnconfirmedCount()).isEqualTo(5);
    }

    @Test
    void healthStatusEndpointsDelegateToTheService() {
        HealthStatusService service = mock(HealthStatusService.class);
        HealthStatusController controller = new HealthStatusController(service);

        controller.reportStatus(Map.of("anonymousId", "u1", "status", "SUSPECT"));
        controller.reportStatus(Map.of("anonymousId", "u2", "status", "ACTIVE", "adminOverride", true));
        controller.recover("u3");
        controller.resolve(Map.of("anonymousId", "u4"));

        verify(service).updateStatus("u1", "SUSPECT", false);
        verify(service).updateStatus("u2", "ACTIVE", true);
        verify(service).promoteToRecovered("u3");
        verify(service).resolveStatus("u4", false);
    }

    @Test
    void locationSignalsAreValidatedBeforeProcessing() {
        LocationResolutionService service = mock(LocationResolutionService.class);
        LocationSignalController controller = new LocationSignalController(service);
        Map<String, Object> missingAp = new HashMap<>();
        missingAp.put("deviceMac", "dev");
        missingAp.put("rssi", -50);

        assertThat(controller.receiveSignal(Map.of("apMac", "ap", "deviceMac", "dev", "rssi", "-61.5"))
                .getStatusCode().value()).isEqualTo(200);
        verify(service).processSignal("ap", "dev", -61.5);
        assertThat(controller.receiveSignal(missingAp).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void sessionHandshakeRegistersAndClosesMacSessions() {
        MacSessionRegistry registry = mock(MacSessionRegistry.class);
        SessionHandshakeController controller = new SessionHandshakeController(registry);

        assertThat(controller.handshake(Map.of("macAddress", "aa", "anonymousId", "u1")).getStatusCode().value()).isEqualTo(200);
        assertThat(controller.closeSession("aa").getStatusCode().value()).isEqualTo(204);
        verify(registry).registerSession("aa", "u1");
        verify(registry).closeSession("aa");

        MacSessionRegistry unused = mock(MacSessionRegistry.class);
        SessionHandshakeController rejecting = new SessionHandshakeController(unused);
        assertThat(rejecting.handshake(Map.of("macAddress", "aa")).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(unused);
    }
}
