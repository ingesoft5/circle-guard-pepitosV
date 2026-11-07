package com.circleguard.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.promotion.model.jpa.SystemSettings;
import com.circleguard.promotion.repository.jpa.SystemSettingsRepository;
import com.circleguard.promotion.support.Neo4jClientStub;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

class StatusLifecycleServiceUnitTest {

    private Neo4jClientStub neo4j;
    private SystemSettingsRepository settingsRepository;
    private ValueOperations<String, String> values;
    private KafkaTemplate<String, Object> kafka;
    private StatusLifecycleService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        neo4j = new Neo4jClientStub();
        settingsRepository = mock(SystemSettingsRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        kafka = mock(KafkaTemplate.class);
        service = new StatusLifecycleService(neo4j.client, settingsRepository, redis, kafka);
        when(settingsRepository.getSettings()).thenReturn(Optional.of(SystemSettings.builder().mandatoryFenceDays(10).build()));
    }

    @Test
    void expiredFencesAreReleasedAndBroadcast() {
        neo4j.allResults(List.of(Map.of("id", "u1", "updated", 1L, "thresh", 2L)));
        neo4j.oneResults(Map.of("releasedIds", List.of("u1", "u2")));

        long before = System.currentTimeMillis();
        service.processAutomaticTransitions();

        assertThat((Long) neo4j.bound("threshold")).isLessThanOrEqualTo(before - 10L * 24 * 60 * 60 * 1000 + 1000);
        verify(values).multiSet(Map.of("user:status:u1", "ACTIVE", "user:status:u2", "ACTIVE"));
        verify(kafka).send(eq("promotion.status.changed"), eq("u1"), any());
        verify(kafka).send(eq("promotion.status.changed"), eq("u2"), any());
    }

    @Test
    void nothingToReleaseChangesNothing() {
        neo4j.oneResults(Map.of("releasedIds", List.of()));

        service.processAutomaticTransitions();

        verify(values, never()).multiSet(anyMap());
        verify(kafka, never()).send(anyString(), anyString(), any());
    }

    @Test
    void noResultRowChangesNothing() {
        service.processAutomaticTransitions();

        verify(values, never()).multiSet(anyMap());
    }

    @Test
    void requiresSystemSettings() {
        when(settingsRepository.getSettings()).thenReturn(Optional.empty());

        assertThatThrownBy(service::processAutomaticTransitions).isInstanceOf(IllegalStateException.class);
    }
}
