package com.circleguard.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class TraceparentPropagationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    @Test
    void aPublishedRecordCarriesTheCurrentTraceparent() {
        Tracer tracer = currentTrace();
        TraceparentProducerInterceptor interceptor = new TraceparentProducerInterceptor();
        interceptor.configure(Map.of(TraceparentProducerInterceptor.CONFIG_KEY, tracer));
        ProducerRecord<Object, Object> record = new ProducerRecord<>("survey.submitted", "visitor", Map.of());
        interceptor.onSend(record);
        Header header = record.headers().lastHeader(TraceparentHeaders.HEADER);
        assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo("00-" + TRACE_ID + "-" + SPAN_ID + "-01");
        when(tracer.currentSpan()).thenReturn(null);
        interceptor.onSend(record);
        interceptor.onAcknowledgement(null, null);
        interceptor.close();
        new TraceparentProducerInterceptor().onSend(record);
    }

    @Test
    void aConsumedRecordKeepsTheSameTraceId() {
        ConsumerRecord<Object, Object> incoming = new ConsumerRecord<>("survey.submitted", 0, 0L, "visitor", Map.of());
        TraceparentHeaders.put(incoming.headers(), TRACE_ID, SPAN_ID, true);
        TraceparentRecordInterceptor interceptor = new TraceparentRecordInterceptor();
        interceptor.intercept(incoming, null);
        assertThat(MDC.get("traceId")).isEqualTo(TRACE_ID);
        interceptor.afterRecord(incoming, null);
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void theProducerFactoryReceivesTheInterceptor() {
        Tracer tracer = currentTrace();
        Map<String, Object> configs = KafkaTracingConfig.producerConfigs(Map.of("bootstrap.servers", "localhost:9092"), tracer);
        assertThat(configs)
                .containsEntry(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, TraceparentProducerInterceptor.class.getName())
                .containsEntry(TraceparentProducerInterceptor.CONFIG_KEY, tracer);
        KafkaTracingConfig config = new KafkaTracingConfig();
        config.traceparentProducer(tracer).customize(new DefaultKafkaProducerFactory<>(Map.of("bootstrap.servers", "localhost:9092")));
        assertThat(config.traceparentConsumer()).isInstanceOf(TraceparentRecordInterceptor.class);
    }

    private static Tracer currentTrace() {
        TraceContext context = mock(TraceContext.class);
        when(context.traceId()).thenReturn(TRACE_ID);
        when(context.spanId()).thenReturn(SPAN_ID);
        when(context.sampled()).thenReturn(true);
        io.micrometer.tracing.Span span = mock(io.micrometer.tracing.Span.class);
        when(span.context()).thenReturn(context);
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(span);
        return tracer;
    }
}
