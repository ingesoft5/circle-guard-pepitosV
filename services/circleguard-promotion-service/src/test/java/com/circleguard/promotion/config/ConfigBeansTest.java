package com.circleguard.promotion.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.circleguard.promotion.support.Neo4jClientStub;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

class ConfigBeansTest {

    @Test
    void cacheManagerKnowsTheServiceCaches() {
        CacheManager manager = new CacheConfig().cacheManager();

        assertThat(manager.getCacheNames())
                .containsExactlyInAnyOrder("userStatus", "proximityMatches", "buildingMetadata", "systemSettings");
    }

    @Test
    void schemaInitialisationCreatesTheIndexes() {
        Neo4jClientStub neo4j = new Neo4jClientStub();

        new Neo4jSchemaConfig(neo4j.client).initializeSchema();

        assertThat(neo4j.queries).hasSize(6);
        assertThat(neo4j.queries.subList(0, 5)).allMatch(query -> query.startsWith("CREATE INDEX"));
        assertThat(neo4j.queries.get(5)).contains("MEMBER_OF");
    }
}
