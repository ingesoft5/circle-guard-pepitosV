package com.circleguard.tracing;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public class TraceparentProducerInterceptor implements ProducerInterceptor<Object, Object> {

    static final String CONFIG_KEY = TraceparentProducerInterceptor.class.getName();

    private Tracer tracer;

    @Override
    public void configure(Map<String, ?> configs) {
        Object value = configs.get(CONFIG_KEY);
        if (value instanceof Tracer configured) {
            this.tracer = configured;
        }
    }

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        if (tracer == null || tracer.currentSpan() == null) {
            return record;
        }
        TraceContext context = tracer.currentSpan().context();
        TraceparentHeaders.put(
                record.headers(),
                context.traceId(),
                context.spanId(),
                Boolean.TRUE.equals(context.sampled()));
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
    }

    @Override
    public void close() {
    }
}
