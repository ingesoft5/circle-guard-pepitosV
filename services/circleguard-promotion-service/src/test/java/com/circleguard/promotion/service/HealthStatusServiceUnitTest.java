package com.circleguard.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.promotion.exception.FenceException;
import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.circleguard.promotion.support.Neo4jClientStub;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

class HealthStatusServiceUnitTest {

    private static final long DAY = 24L * 60 * 60 * 1000;

    private UserNodeRepository userRepository;
    private Neo4jClientStub neo4j;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private KafkaTemplate<String, Object> kafka;
    private SystemSettingsRepository settingsRepository;
    private CircleNodeRepository circleRepository;
    private HealthStatusService service;
    // multiSet receives one HashMap that the service clears and refills for each
    // batch, so every call is copied here instead of keeping the reference.
    private final List<Map<String, String>> redisBatches = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserNodeRepository.class);
        neo4j = new Neo4jClientStub();
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            redisBatches.add(new HashMap<>(invocation.<Map<String, String>>getArgument(0)));
            return null;
        }).when(values).multiSet(anyMap());
        kafka = mock(KafkaTemplate.class);
        settingsRepository = mock(SystemSettingsRepository.class);
        circleRepository = mock(CircleNodeRepository.class);
        when(circleRepository.findNewlyFencedCircles(anyString())).thenReturn(List.of());
        service = new HealthStatusService(userRepository, neo4j.client, redis, kafka, settingsRepository, circleRepository);
    }

    private static SystemSettings settings(int fenceDays, int windowDays) {
        return SystemSettings.builder()
                .mandatoryFenceDays(fenceDays)
                .encounterWindowDays(windowDays)
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(3600L)
                .build();
    }

    private static Map<String, Object> propagation(List<Map<String, String>> affected) {
        Map<String, Object> row = new HashMap<>();
        row.put("sourceId", "source");
        row.put("affectedContacts", affected);
        return row;
    }

    private Map<String, String> redisWrites() {
        assertThat(redisBatches).as("Redis multiSet calls").isNotEmpty();
        Map<String, String> all = new HashMap<>();
        redisBatches.forEach(all::putAll);
        return all;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sent(String topic, String key) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(topic), eq(key), payload.capture());
        return (Map<String, Object>) payload.getValue();
    }

    @Test
    void confirmedCaseUpdatesContactsCachesAndRaisesAPriorityAlert() {
        when(settingsRepository.getSettings()).thenReturn(Optional.of(settings(14, 7)));
        neo4j.oneResults(propagation(List.of(
                Map.of("id", "contact-1", "status", "SUSPECT"),
                Map.of("id", "contact-2", "status", "PROBABLE"))));

        long before = System.currentTimeMillis();
        service.updateStatus("source", "CONFIRMED");

        assertThat(neo4j.bound("id")).isEqualTo("source");
        assertThat(neo4j.bound("status")).isEqualTo("CONFIRMED");
        assertThat((Long) neo4j.bound("threshold")).isBetween(before - 7 * DAY - 1000, before - 7 * DAY + 1000);
        assertThat(redisWrites()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "user:status:source", "CONFIRMED",
                "user:status:contact-1", "SUSPECT",
                "user:status:contact-2", "PROBABLE"));
        assertThat(sent("promotion.status.changed", "source")).containsEntry("status", "CONFIRMED");
        assertThat(sent("alert.priority", "source"))
                .containsEntry("eventType", "CONFIRMED_CASE")
                .containsEntry("affectedCount", 2);
    }

    @Test
    void largeOutbreakOfSuspectsRaisesAPriorityAlert() {
        List<Map<String, String>> affected = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            affected.add(Map.of("id", "c" + i, "status", "PROBABLE"));
        }
        neo4j.oneResults(propagation(affected));

        service.updateStatus("source", "SUSPECT");

        assertThat(sent("alert.priority", "source"))
                .containsEntry("eventType", "LARGE_OUTBREAK")
                .containsEntry("affectedCount", 21);
    }

    @Test
    void suspectWithFewContactsDoesNotRaiseAnAlert() {
        List<Map<String, String>> affected = new ArrayList<>();
        affected.add(Map.of("id", "c1", "status", "PROBABLE"));
        affected.add(null);
        Map<String, String> withoutId = new HashMap<>();
        withoutId.put("status", "PROBABLE");
        affected.add(withoutId);
        neo4j.oneResults(propagation(affected));

        service.updateStatus("source", "SUSPECT");

        assertThat(redisWrites()).containsOnlyKeys("user:status:source", "user:status:c1");
        verify(kafka, never()).send(eq("alert.priority"), anyString(), any());
    }

    @Test
    void newlyFencedCirclesAreBroadcast() {
        neo4j.oneResults(propagation(null));
        when(circleRepository.findNewlyFencedCircles("source")).thenReturn(List.of(
                CircleNode.builder().id(42L).name("Lab 3").locationId("ap-1").build()));

        service.updateStatus("source", "SUSPECT");

        assertThat(sent("circle.fenced", "42"))
                .containsEntry("circleId", "42")
                .containsEntry("locationId", "ap-1")
                .containsEntry("name", "Lab 3");
        assertThat(redisWrites()).containsOnlyKeys("user:status:source");
    }

    @Test
    void unknownUserChangesNothingDownstream() {
        service.updateStatus("ghost", "SUSPECT");

        verify(values, never()).multiSet(anyMap());
        verify(kafka, never()).send(anyString(), anyString(), any());
    }

    @Test
    void redisWritesAreBatchedInChunksOf2000() {
        List<Map<String, String>> affected = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            affected.add(Map.of("id", "c" + i, "status", "PROBABLE"));
        }
        neo4j.oneResults(propagation(affected));

        service.updateStatus("source", "SUSPECT");

        verify(values, times(2)).multiSet(anyMap());
        assertThat(redisBatches).extracting(Map::size).containsExactly(2000, 501);
        assertThat(redisWrites()).hasSize(2501);
    }

    @Test
    void returningToActiveInsideTheFenceWindowIsRejected() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                UserNode.builder().anonymousId("u1").status("SUSPECT").statusUpdatedAt(System.currentTimeMillis() - DAY).build()));
        when(settingsRepository.getSettings()).thenReturn(Optional.of(settings(14, 14)));

        assertThatThrownBy(() -> service.updateStatus("u1", "ACTIVE"))
                .isInstanceOf(FenceException.class)
                .hasMessageContaining("mandatory fence window");
        assertThat(neo4j.queries).isEmpty();
    }

    @Test
    void returningToActiveAfterTheFenceWindowIsAllowed() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                UserNode.builder().anonymousId("u1").status("PROBABLE").statusUpdatedAt(System.currentTimeMillis() - 20 * DAY).build()));
        when(settingsRepository.getSettings()).thenReturn(Optional.of(settings(14, 14)));
        neo4j.oneResults(propagation(List.of()));

        service.updateStatus("u1", "ACTIVE");

        assertThat(sent("promotion.status.changed", "u1")).containsEntry("status", "ACTIVE");
    }

    @Test
    void adminOverrideSkipsTheFenceCheck() {
        neo4j.oneResults(propagation(List.of()));

        service.updateStatus("u1", "ACTIVE", true);

        verify(userRepository, never()).findById(anyString());
    }

    @Test
    void fenceCheckNeedsSystemSettings() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                UserNode.builder().anonymousId("u1").status("SUSPECT").statusUpdatedAt(System.currentTimeMillis()).build()));
        when(settingsRepository.getSettings()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveStatus("u1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void usersOutsideAFencedStatusCanAlwaysBeResolved() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(
                UserNode.builder().anonymousId("u1").status("CONFIRMED").statusUpdatedAt(System.currentTimeMillis()).build()));

        service.resolveStatus("u1");

        assertThat(redisWrites()).containsEntry("user:status:u1", "ACTIVE");
    }

    @Test
    void resolvingReleasesDirectAndSecondHopContacts() {
        when(userRepository.findById("source")).thenReturn(Optional.empty());
        List<String> phase1 = new ArrayList<>(List.of("l1-a", "l1-b"));
        phase1.add(null);
        List<String> phase2 = new ArrayList<>(List.of("l2-a"));
        phase2.add(null);
        neo4j.oneResults(Map.of("releasedIds", phase1), Map.of("releasedIds", phase2));

        service.resolveStatus("source");

        assertThat(neo4j.queries).hasSize(3);
        assertThat(neo4j.bound("l1Ids")).isEqualTo(phase1);
        assertThat(neo4j.bound("sourceId")).isEqualTo("source");
        assertThat(redisWrites()).containsOnlyKeys(
                "user:status:source", "user:status:l1-a", "user:status:l1-b", "user:status:l2-a");
        assertThat(sent("promotion.status.changed", "source")).containsEntry("status", "ACTIVE");
    }

    @Test
    void resolvingWithNoReleasedContactsOnlyResetsTheSource() {
        Map<String, Object> noIds = new HashMap<>();
        noIds.put("releasedIds", null);
        neo4j.oneResults(noIds, null);

        service.resolveStatus("source", true);

        assertThat(redisWrites()).containsOnlyKeys("user:status:source");
    }

    @Test
    void recoveryResolvesThenGrantsThirtyDaysOfImmunity() {
        neo4j.oneResults(null, null);

        service.promoteToRecovered("u1");

        assertThat(neo4j.queries.get(neo4j.queries.size() - 1)).contains("SET u.status = 'RECOVERED'");
        verify(values).set("user:status:u1", "RECOVERED");
        verify(redis).expire("user:status:u1", Duration.ofDays(30));
    }

    @Test
    void cachedStatusIsReadFromRedis() {
        when(values.get("user:status:u1")).thenReturn("SUSPECT");

        assertThat(service.getCachedStatus("u1")).isEqualTo("SUSPECT");
        service.evictUserCache("u1");
    }
}
