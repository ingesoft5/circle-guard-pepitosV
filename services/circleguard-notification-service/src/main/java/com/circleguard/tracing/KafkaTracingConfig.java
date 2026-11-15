package com.circleguard.tracing;

import io.micrometer.tracing.Tracer;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.listener.RecordInterceptor;

@Configuration
@ConditionalOnBean(Tracer.class)
public class KafkaTracingConfig {

    @Bean
    public DefaultKafkaProducerFactoryCustomizer traceparentProducer(Tracer tracer) {
        return factory -> {
            if (factory instanceof DefaultKafkaProducerFactory<?, ?> producerFactory) {
                producerFactory.updateConfigs(producerConfigs(producerFactory.getConfigurationProperties(), tracer));
            }
        };
    }

    @Bean
    public RecordInterceptor<Object, Object> traceparentConsumer() {
        return new TraceparentRecordInterceptor();
    }

    static Map<String, Object> producerConfigs(Map<String, Object> current, Tracer tracer) {
        Map<String, Object> configs = new HashMap<>(current);
        configs.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, TraceparentProducerInterceptor.class.getName());
        configs.put(TraceparentProducerInterceptor.CONFIG_KEY, tracer);
        return configs;
    }
}
