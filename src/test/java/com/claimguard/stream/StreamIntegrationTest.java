package com.claimguard.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimguard.alerts.AlertRepository;
import com.claimguard.screening.StreamStats;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end stream test against real Kafka and Redis containers.
 *
 * <p>Excluded from the default run; execute with {@code mvn test -Pintegration}.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class StreamIntegrationTest {

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        registry.add("claimguard.kafka.enabled", () -> "true");
        registry.add("claimguard.history.store", () -> "redis");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired AlertRepository alerts;
    @Autowired StreamStats stats;
    @Autowired StringRedisTemplate redisTemplate;

    private static String claimJson(String id) {
        return """
                {"claimId":"%s","policyNumber":"POL-%s","policyStartDate":"2026-01-01","sumInsured":500000,
                 "lineOfBusiness":"MOTOR","vehicleRegNo":"KA01AB%s","providerId":"PRV-%s",
                 "claimantMobile":"9876543210","lossDate":"2026-01-07","reportedAt":"2026-02-01T10:00:00Z",
                 "claimedAmount":450000}
                """.formatted(id, id, id, id);
    }

    @Test
    void duplicateIsIgnoredAndMalformedGoesToDltAndConsumerKeepsGoing() throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            send(producer, "POL-INT-1", claimJson("INT-1"));       // high score -> alert
            send(producer, "POL-INT-1", claimJson("INT-1"));       // duplicate
            send(producer, "bad", "this is not json");             // -> DLT, no retry
            send(producer, "POL-INT-2", claimJson("INT-2"));       // high score -> alert
        }

        // INT-2 proves the consumer survived the bad message and kept going.
        Awaitility.await().atMost(Duration.ofSeconds(60))
                .until(() -> alerts.existsByClaimId("INT-2"));
        Awaitility.await().atMost(Duration.ofSeconds(60))
                .until(() -> stats.snapshot().get("deadLettered") == 1L);

        long int1Rows = alerts.findAll().stream().filter(a -> "INT-1".equals(a.getClaimId())).count();
        assertThat(int1Rows).as("the duplicate must not create a second alert").isEqualTo(1);

        assertThat(stats.snapshot().get("duplicatesSkipped")).isGreaterThanOrEqualTo(1L);
        assertThat(stats.snapshot().get("deadLettered")).isEqualTo(1L);

        assertThat(pollDlt()).as("exactly one bad record is dead-lettered").hasSize(1);

        Awaitility.await().atMost(Duration.ofSeconds(30))
                .until(() -> {
                    Long ttl = redisTemplate.getExpire("claims:policy:POL-INT-1");
                    return ttl != null && ttl > 0;
                });
    }

    private static void send(KafkaProducer<String, String> producer, String key, String value) {
        producer.send(new ProducerRecord<>("claims.submitted", key, value));
    }

    private static Properties producerProps() {
        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        props.put("acks", "all");
        return props;
    }

    /** Reads the DLT from the beginning with a throwaway group so the assertion is deterministic. */
    private static List<ConsumerRecord<String, String>> pollDlt() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-verifier-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("claims.submitted.DLT"));
            long deadline = System.currentTimeMillis() + 30_000;
            while (received.isEmpty() && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                records.forEach(received::add);
            }
        }
        return received;
    }
}
