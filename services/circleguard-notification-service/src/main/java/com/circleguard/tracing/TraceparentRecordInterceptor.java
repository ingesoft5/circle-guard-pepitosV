package com.circleguard.tracing;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

public class TraceparentRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        return remember(record);
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove("traceId");
    }

    private ConsumerRecord<Object, Object> remember(ConsumerRecord<Object, Object> record) {
        Header header = record.headers().lastHeader(TraceparentHeaders.HEADER);
        if (header != null) {
            MDC.put("traceId", TraceparentHeaders.traceId(new String(header.value(), StandardCharsets.UTF_8)));
        }
        return record;
    }
}
