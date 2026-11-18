package com.circleguard.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;

class JsonLogFormatTest {

    @Test
    void configurationDeclaresJsonWithServiceAndTraceId() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
        assertThat(xml).contains("LogstashEncoder");
        assertThat(xml).contains("<timestamp>timestamp</timestamp>");
        assertThat(xml).contains("traceId");
        assertThat(xml).contains("spanId");
        assertThat(xml).contains("spring.application.name");
        assertThat(xml).contains("\"service\":\"${service}\"");
    }

    @Test
    void aLogEventCarriesTimestampLevelServiceAndTraceId() {
        LoggerContext context = new LoggerContext();
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);
        encoder.setIncludeContext(false);
        encoder.getFieldNames().setTimestamp("timestamp");
        encoder.addIncludeMdcKeyName("traceId");
        encoder.addIncludeMdcKeyName("spanId");
        encoder.setCustomFields("{\"service\":\"circleguard-form-service\"}");
        context.start();
        encoder.start();

        LoggingEvent event = new LoggingEvent();
        event.setLoggerContext(context);
        event.setLoggerName("json-log");
        event.setLevel(Level.INFO);
        event.setMessage("visitor checked in");
        event.setTimeStamp(System.currentTimeMillis());
        event.setMDCPropertyMap(Map.of(
                "traceId", "4bf92f3577b34da6a3ce929d0e0e4736",
                "spanId", "00f067aa0ba902b7"));

        String json = new String(encoder.encode(event), StandardCharsets.UTF_8);
        assertThat(json).contains("\"timestamp\"");
        assertThat(json).contains("\"level\":\"INFO\"");
        assertThat(json).contains("\"service\":\"circleguard-form-service\"");
        assertThat(json).contains("\"traceId\":\"4bf92f3577b34da6a3ce929d0e0e4736\"");
        assertThat(json).contains("\"spanId\":\"00f067aa0ba902b7\"");
        encoder.stop();
    }
}
