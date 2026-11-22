package com.circleguard.form.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.form.model.OutboxEvent;
import com.circleguard.form.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxServiceTest {

    private OutboxEventRepository repository;
    private Tracer tracer;
    private OutboxService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        tracer = mock(Tracer.class);
        service = new OutboxService(repository, objectMapper, tracer);
        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void enqueueSerializesTheEventAndStoresTheTraceparent() throws Exception {
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        when(context.spanId()).thenReturn("00f067aa0ba902b7");
        when(context.sampled()).thenReturn(Boolean.TRUE);

        service.enqueue("HealthSurvey", "survey-id", "survey.submitted",
                "survey.submitted", "key-1", Map.of("hasSymptoms", true));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("HealthSurvey");
        assertThat(saved.getAggregateId()).isEqualTo("survey-id");
        assertThat(saved.getEventType()).isEqualTo("survey.submitted");
        assertThat(saved.getTopic()).isEqualTo("survey.submitted");
        assertThat(saved.getPartitionKey()).isEqualTo("key-1");
        assertThat(objectMapper.readTree(saved.getPayload()).get("hasSymptoms").asBoolean()).isTrue();
        assertThat(saved.getTraceparent())
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getPublishedAt()).isNull();
    }

    @Test
    void withoutAnActiveSpanTheTraceparentIsNull() {
        when(tracer.currentSpan()).thenReturn(null);

        service.enqueue("HealthSurvey", "survey-id", "survey.submitted",
                "survey.submitted", "key-1", Map.of("hasSymptoms", false));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTraceparent()).isNull();
    }
}
