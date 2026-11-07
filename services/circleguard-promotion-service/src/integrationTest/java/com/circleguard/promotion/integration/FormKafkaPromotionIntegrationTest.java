package com.circleguard.promotion.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.circleguard.form.FormApplication;
import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.service.HealthSurveyService;
import com.circleguard.form.service.QuestionnaireService;
import com.circleguard.promotion.PromotionApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * form-service publishes a symptomatic questionnaire and promotion-service applies it in Neo4j.
 */
@Testcontainers
class FormKafkaPromotionIntegrationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.1"));

    @Container
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.12").withAdminPassword("password");

    @Container
    static final PostgreSQLContainer<?> formDb = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final PostgreSQLContainer<?> promotionDb = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.2.1").withExposedPorts(6379);

    private static ConfigurableApplicationContext formContext;
    private static ConfigurableApplicationContext promotionContext;

    @BeforeAll
    static void startServices() {
        promotionContext = start(PromotionApplication.class, "circleguard-promotion-service", promotionOverrides());
        formContext = start(FormApplication.class, "circleguard-form-service", formOverrides());
    }

    @AfterAll
    static void stopServices() {
        if (formContext != null) {
            formContext.close();
        }
        if (promotionContext != null) {
            promotionContext.close();
        }
    }

    @Test
    void symptomaticQuestionnairePromotesTheUserInNeo4j() {
        String anonymousId = UUID.randomUUID().toString();
        Neo4jClient neo4jClient = promotionContext.getBean(Neo4jClient.class);
        neo4jClient.query("CREATE (:User {anonymousId: $id, status: 'ACTIVE'})")
                .bind(anonymousId)
                .to("id")
                .run();

        UUID questionId = saveSymptomQuestion();
        HealthSurvey survey = HealthSurvey.builder()
                .anonymousId(UUID.fromString(anonymousId))
                .responses(Map.of(questionId.toString(), "[\"Headache\"]"))
                .build();

        HealthSurvey saved = formContext.getBean(HealthSurveyService.class).submitSurvey(survey);

        assertThat(saved.getHasFever()).isTrue();
        await().atMost(Duration.ofSeconds(45)).untilAsserted(() -> assertThat(statusOf(neo4jClient, anonymousId))
                .isEqualTo("SUSPECT"));
    }

    private static UUID saveSymptomQuestion() {
        Question question = Question.builder()
                .text("Which symptoms do you have?")
                .type(QuestionType.MULTI_CHOICE)
                .orderIndex(0)
                .build();
        Questionnaire questionnaire = Questionnaire.builder()
                .title("Daily health")
                .version(1)
                .isActive(true)
                .questions(new ArrayList<>(List.of(question)))
                .build();

        TransactionTemplate transaction = new TransactionTemplate(
                formContext.getBean("transactionManager", PlatformTransactionManager.class));
        return transaction.execute(status -> {
            Questionnaire saved = formContext.getBean(QuestionnaireService.class).saveQuestionnaire(questionnaire);
            return saved.getQuestions().get(0).getId();
        });
    }

    private static String statusOf(Neo4jClient neo4jClient, String anonymousId) {
        return neo4jClient.query("MATCH (u:User {anonymousId: $id}) RETURN u.status AS status")
                .bind(anonymousId)
                .to("id")
                .fetch()
                .one()
                .map(row -> String.valueOf(row.get("status")))
                .orElse(null);
    }

    private static ConfigurableApplicationContext start(Class<?> source, String service, Map<String, Object> overrides) {
        Path module = module(service);
        String config = module.resolve("src/main/resources/application.yml").toUri().toString();
        return new SpringApplicationBuilder(source)
                .initializers(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("integration-test", overrides)))
                .run("--spring.config.location=" + config);
    }

    private static Map<String, Object> promotionOverrides() {
        Path migrations = module("circleguard-promotion-service").resolve("src/main/resources/db/migration");
        Map<String, Object> properties = sharedDataSource(promotionDb, migrations);
        properties.put("spring.neo4j.uri", neo4j.getBoltUrl());
        properties.put("spring.neo4j.authentication.username", "neo4j");
        properties.put("spring.neo4j.authentication.password", "password");
        properties.put("spring.data.redis.host", redis.getHost());
        properties.put("spring.data.redis.port", redis.getMappedPort(6379).toString());
        properties.put("spring.kafka.bootstrap-servers", bootstrapServers());
        properties.put("spring.kafka.consumer.auto-offset-reset", "earliest");
        properties.put("spring.kafka.listener.missing-topics-fatal", "false");
        return properties;
    }

    private static Map<String, Object> formOverrides() {
        Path migrations = module("circleguard-form-service").resolve("src/main/resources/db/migration");
        Map<String, Object> properties = sharedDataSource(formDb, migrations);
        properties.put("spring.kafka.bootstrap-servers", bootstrapServers());
        properties.put("spring.kafka.producer.properties.linger.ms", "0");
        // Promotion's Neo4j and Redis libraries are on this classpath, but form must keep a single JPA transaction manager.
        properties.put("spring.autoconfigure.exclude", String.join(",",
                "org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveDataAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveRepositoriesAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"));
        return properties;
    }

    private static Map<String, Object> sharedDataSource(PostgreSQLContainer<?> database, Path migrations) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("server.port", "0");
        properties.put("spring.flyway.locations", "filesystem:" + migrations.toString().replace('\\', '/'));
        properties.put("spring.datasource.url", database.getJdbcUrl());
        properties.put("spring.datasource.username", database.getUsername());
        properties.put("spring.datasource.password", database.getPassword());
        return properties;
    }

    private static Path module(String service) {
        Path cwd = Path.of("").toAbsolutePath();
        Path nested = cwd.resolve("services").resolve(service);
        if (Files.isDirectory(nested)) {
            return nested;
        }
        Path sibling = cwd.getParent().getParent().resolve("services").resolve(service);
        if (Files.isDirectory(sibling)) {
            return sibling;
        }
        throw new IllegalStateException("Cannot find the module directory for " + service + " from " + cwd);
    }

    private static String bootstrapServers() {
        return kafka.getBootstrapServers().replaceFirst("^[A-Za-z]+://", "");
    }
}
