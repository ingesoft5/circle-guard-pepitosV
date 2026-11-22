package com.circleguard.form.service;

import com.circleguard.form.model.OutboxEvent;
import com.circleguard.form.repository.OutboxEventRepository;
import com.circleguard.tracing.TraceparentHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores an event in the same transaction as the business change (HU-38). The relay publishes
 * it later, so a Kafka outage cannot lose the event.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    @Transactional
    public void enqueue(String aggregateType,
                        String aggregateId,
                        String eventType,
                        String topic,
                        String partitionKey,
                        Object event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize the outbox event", e);
        }

        OutboxEvent outbox = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(topic)
                .partitionKey(partitionKey)
                .payload(payload)
                .traceparent(currentTraceparent())
                .createdAt(Instant.now())
                .attempts(0)
                .build();
        repository.save(outbox);
        log.debug("outbox: queued {} for {}", eventType, partitionKey);
    }

    private String currentTraceparent() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        TraceContext context = span.context();
        return TraceparentHeaders.format(
                context.traceId(), context.spanId(), Boolean.TRUE.equals(context.sampled()));
    }
}
