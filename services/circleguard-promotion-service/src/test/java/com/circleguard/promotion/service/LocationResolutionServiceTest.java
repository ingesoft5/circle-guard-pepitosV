package com.circleguard.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class LocationResolutionServiceTest {

    private AccessPointRepository accessPoints;
    private MacSessionRegistry sessions;
    private GraphService graphService;
    private KafkaTemplate<String, Object> kafka;
    private StringRedisTemplate redis;
    private SetOperations<String, String> sets;
    private LocationResolutionService service;

    private final UUID apId = UUID.randomUUID();
    private final UUID buildingId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();
    private AccessPoint ap;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        accessPoints = mock(AccessPointRepository.class);
        sessions = mock(MacSessionRegistry.class);
        graphService = mock(GraphService.class);
        kafka = mock(KafkaTemplate.class);
        redis = mock(StringRedisTemplate.class);
        sets = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(sets);
        service = new LocationResolutionService(accessPoints, sessions, graphService, kafka, redis);

        Building building = Building.builder().id(buildingId).build();
        Floor floor = Floor.builder().id(floorId).floorNumber(2).building(building).build();
        ap = AccessPoint.builder().id(apId).name("Hall").floor(floor).coordinateX(1.5).coordinateY(2.5).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void knownDeviceNearAKnownAccessPointIsResolved() {
        when(accessPoints.findByMacAddress("ap-mac")).thenReturn(Optional.of(ap));
        when(sessions.getAnonymousId("dev-mac")).thenReturn("anon-1");
        when(sets.members("spatial:location:" + apId)).thenReturn(Set.of("anon-1", "anon-2"));

        service.processSignal("ap-mac", "dev-mac", -60.0);

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("proximity.detected"), eq("anon-1"), event.capture());
        Map<String, Object> payload = (Map<String, Object>) event.getValue();
        assertThat(payload)
                .containsEntry("buildingId", buildingId)
                .containsEntry("floorId", floorId)
                .containsEntry("rssi", -60.0)
                .containsEntry("coordinates", Map.of("x", 1.5, "y", 2.5));
        verify(graphService).recordEncounter("anon-1", "anon-2", apId.toString());
        verify(graphService, never()).recordEncounter("anon-1", "anon-1", apId.toString());
        verify(sets).add("spatial:location:" + apId, "anon-1");
        verify(redis).expire("spatial:location:" + apId, Duration.ofMinutes(10));
        verify(graphService).detectAndFormCircles(apId.toString());
    }

    @Test
    void firstUserAtALocationRecordsNoEncounters() {
        when(accessPoints.findByMacAddress("ap-mac")).thenReturn(Optional.of(ap));
        when(sessions.getAnonymousId("dev-mac")).thenReturn("anon-1");
        when(sets.members(anyString())).thenReturn(null);

        service.processSignal("ap-mac", "dev-mac", -70.0);

        verify(graphService, never()).recordEncounter(anyString(), anyString(), anyString());
        verify(graphService).detectAndFormCircles(apId.toString());
    }

    @Test
    void unknownAccessPointIsIgnored() {
        when(accessPoints.findByMacAddress("ap-mac")).thenReturn(Optional.empty());

        service.processSignal("ap-mac", "dev-mac", -60.0);

        verifyNoInteractions(sessions, kafka, graphService);
    }

    @Test
    void deviceWithoutAnAppSessionIsIgnored() {
        when(accessPoints.findByMacAddress("ap-mac")).thenReturn(Optional.of(ap));
        when(sessions.getAnonymousId("dev-mac")).thenReturn(null);

        service.processSignal("ap-mac", "dev-mac", -60.0);

        verify(kafka, never()).send(anyString(), anyString(), any());
        verifyNoInteractions(graphService);
    }
}
