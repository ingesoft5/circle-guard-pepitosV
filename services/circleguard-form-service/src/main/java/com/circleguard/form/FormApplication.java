package com.circleguard.form;

import com.circleguard.form.config.OutboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class FormApplication {
    public static void main(String[] args) {
        SpringApplication.run(FormApplication.class, args);
    }
}
