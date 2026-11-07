package com.circleguard.promotion.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.neo4j.core.Neo4jClient;

/**
 * Stubs the fluent Neo4jClient API (query → bind/to → fetch/run) with one shared spec,
 * records every Cypher query and every bound parameter, and returns the queued results.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class Neo4jClientStub {

    public final Neo4jClient client = mock(Neo4jClient.class);
    public final List<String> queries = new ArrayList<>();
    public final List<String> boundNames = new ArrayList<>();
    public final List<Object> boundValues = new ArrayList<>();

    private final Neo4jClient.UnboundRunnableSpec spec = mock(Neo4jClient.UnboundRunnableSpec.class);
    private final Neo4jClient.RecordFetchSpec fetchSpec = mock(Neo4jClient.RecordFetchSpec.class);

    public Neo4jClientStub() {
        Neo4jClient.OngoingBindSpec bindSpec = mock(Neo4jClient.OngoingBindSpec.class);
        when(client.query(anyString())).thenAnswer(invocation -> {
            queries.add(invocation.getArgument(0));
            return spec;
        });
        when(spec.bind(any())).thenAnswer(invocation -> {
            boundValues.add(invocation.getArgument(0));
            return bindSpec;
        });
        when(bindSpec.to(anyString())).thenAnswer(invocation -> {
            boundNames.add(invocation.getArgument(0));
            return spec;
        });
        when(spec.fetch()).thenReturn(fetchSpec);
        when(fetchSpec.one()).thenReturn(Optional.empty());
        when(fetchSpec.all()).thenReturn(List.of());
    }

    /** Results returned by successive fetch().one() calls; null means no row. */
    public Neo4jClientStub oneResults(Map<String, Object>... rows) {
        Optional<Map<String, Object>>[] results = new Optional[rows.length];
        for (int i = 0; i < rows.length; i++) {
            results[i] = Optional.ofNullable(rows[i]);
        }
        when(fetchSpec.one()).thenReturn(results[0], java.util.Arrays.copyOfRange(results, 1, results.length));
        return this;
    }

    /** Rows returned by every fetch().all() call. */
    public Neo4jClientStub allResults(Collection<Map<String, Object>> rows) {
        when(fetchSpec.all()).thenReturn(rows);
        return this;
    }

    public Object bound(String name) {
        int index = boundNames.lastIndexOf(name);
        return index < 0 ? null : boundValues.get(index);
    }
}
