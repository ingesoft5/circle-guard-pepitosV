package com.circleguard.promotion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.support.Neo4jClientStub;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class GraphAndSessionServicesTest {

    @Test
    void encountersAreRecordedWithTheCurrentTime() {
        UserNodeRepository repository = mock(UserNodeRepository.class);
        GraphService service = new GraphService(repository, new Neo4jClientStub().client);

        service.recordEncounter("a", "b", "ap-1");

        verify(repository).recordEncounter(eq("a"), eq("b"), anyLong(), eq("ap-1"));
    }

    @Test
    void circleDetectionRunsForTheLocation() {
        Neo4jClientStub neo4j = new Neo4jClientStub();
        GraphService service = new GraphService(mock(UserNodeRepository.class), neo4j.client);

        service.detectAndFormCircles("ap-1");

        assertThat(neo4j.queries).hasSize(1);
        assertThat(neo4j.queries.get(0)).contains("size(users) >= 3");
        assertThat(neo4j.bound("loc")).isEqualTo("ap-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void macSessionsAreStoredLowercaseForEightHours() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("session:mac:aa:bb:cc")).thenReturn("anon-1");
        MacSessionRegistry registry = new MacSessionRegistry(redis);

        registry.registerSession("AA:BB:CC", "anon-1");
        String resolved = registry.getAnonymousId("Aa:Bb:Cc");
        registry.closeSession("AA:bb:CC");

        verify(values).set("session:mac:aa:bb:cc", "anon-1", Duration.ofHours(8));
        assertThat(resolved).isEqualTo("anon-1");
        verify(redis).delete("session:mac:aa:bb:cc");
    }
}
