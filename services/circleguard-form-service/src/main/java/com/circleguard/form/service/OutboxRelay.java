package com.circleguard.form.service;

import com.circleguard.form.config.OutboxProperties;
import com.circleguard.form.model.OutboxEvent;
import com.circleguard.form.repository.OutboxEventRepository;
import com.circleguard.tracing.TraceparentHeaders;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the pending outbox rows (HU-38). A row is marked as published only after the broker
 * acknowledges it; on failure it stays pending with the error and attempt count.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${outbox.relay.delay:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.claimBatch(properties.getRelay().getBatchSize());
        for (OutboxEvent event : batch) {
            try {
                Map<String, Object> payload = objectMapper.readValue(
                        event.getPayload(), new TypeReference<Map<String, Object>>() { });
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                        event.getTopic(), event.getPartitionKey(), payload);
                if (event.getTraceparent() != null && !event.getTraceparent().isBlank()) {
                    record.headers().add(
                            TraceparentHeaders.HEADER,
                            event.getTraceparent().getBytes(StandardCharsets.UTF_8));
                }
                kafkaTemplate.send(record)
                        .get(properties.getRelay().getSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished();
            } catch (Exception e) {
                event.recordFailure(e.getMessage());
                log.warn("outbox: could not publish {} ({})", event.getId(), event.getTopic(), e);
            }
            repository.save(event);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanup() {
        Instant threshold = Instant.now().minus(properties.getRetention());
        long deleted = repository.deleteByPublishedAtBefore(threshold);
        if (deleted > 0) {
            log.info("outbox: deleted {} published events", deleted);
        }
    }
}
