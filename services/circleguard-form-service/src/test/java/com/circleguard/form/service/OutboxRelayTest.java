package com.circleguard.form.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.circleguard.form.config.OutboxProperties;
import com.circleguard.form.model.OutboxEvent;
import com.circleguard.form.repository.OutboxEventRepository;
import com.circleguard.tracing.TraceparentHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxRelayTest {

    private OutboxEventRepository repository;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private OutboxRelay relay;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        OutboxProperties properties = new OutboxProperties();
        properties.getRelay().setBatchSize(10);
        properties.getRelay().setSendTimeout(Duration.ofSeconds(1));
        relay = new OutboxRelay(repository, kafkaTemplate, objectMapper, properties);
    }

    private OutboxEvent pendingEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("HealthSurvey")
                .aggregateId("survey-id")
                .eventType("survey.submitted")
                .topic("survey.submitted")
                .partitionKey("key-1")
                .payload("{\"anonymousId\":\"key-1\",\"hasSymptoms\":true}")
                .traceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .createdAt(Instant.now())
                .attempts(0)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aKafkaFailureLeavesTheEventPendingForTheNextAttempt() {
        OutboxEvent event = pendingEvent();
        when(repository.claimBatch(10)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new RuntimeException("kafka down"));

        relay.publishPending();

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("kafka down");
        verify(repository).save(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anInterruptionReInterruptsTheThreadAndLeavesTheEventPending() throws Exception {
        OutboxEvent event = pendingEvent();
        when(repository.claimBatch(10)).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        doThrow(new InterruptedException("stop"))
                .when(future).get(anyLong(), any(TimeUnit.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        assertThatThrownBy(relay::publishPending).isInstanceOf(IllegalStateException.class);

        assertThat(Thread.interrupted()).as("thread must be re-interrupted").isTrue();
        assertThat(event.getPublishedAt()).isNull();
        verify(repository, never()).save(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSuccessfulSendMarksTheEventPublishedAndKeepsTheTraceparent() {
        OutboxEvent event = pendingEvent();
        when(repository.claimBatch(10)).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, Object>> ack =
                CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(ack);

        relay.publishPending();

        ArgumentCaptor<ProducerRecord<String, Object>> record =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(record.capture());
        Header traceparent = record.getValue().headers().lastHeader(TraceparentHeaders.HEADER);
        assertThat(traceparent).isNotNull();
        assertThat(new String(traceparent.value(), UTF_8))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getLastError()).isNull();
        verify(repository).save(event);
    }
}
