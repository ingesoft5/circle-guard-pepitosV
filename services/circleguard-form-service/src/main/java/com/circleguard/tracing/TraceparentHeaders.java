package com.circleguard.tracing;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;

public final class TraceparentHeaders {

    public static final String HEADER = "traceparent";

    private TraceparentHeaders() {
    }

    public static String format(String traceId, String spanId, boolean sampled) {
        return "00-" + traceId + "-" + spanId + "-" + (sampled ? "01" : "00");
    }

    public static String traceId(String header) {
        String[] parts = header == null ? new String[0] : header.split("-");
        return parts.length >= 2 ? parts[1] : "";
    }

    public static void put(Headers headers, String traceId, String spanId, boolean sampled) {
        headers.remove(HEADER);
        headers.add(HEADER, format(traceId, spanId, sampled).getBytes(StandardCharsets.UTF_8));
    }
}
