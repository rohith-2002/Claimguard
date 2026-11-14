package com.claimguard.stream;

import com.claimguard.screening.StreamStats;
import jakarta.validation.ConstraintViolationException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka wiring for the consumer: the topics, and a bounded-retry error handler that dead-letters the
 * message after the retries are exhausted so the listener keeps going.
 *
 * <p>Spring Boot applies a single {@code CommonErrorHandler} bean to the default listener container, so the
 * handler below governs {@link ClaimConsumer}.
 */
@Configuration
@ConditionalOnProperty(name = "claimguard.kafka.enabled", havingValue = "true")
class KafkaConfig {

    static final String TOPIC = "claims.submitted";
    static final String DLT = TOPIC + ".DLT";

    @Bean
    NewTopic claimsTopic() {
        return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic claimsDltTopic() {
        return TopicBuilder.name(DLT).partitions(1).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, StreamStats stats) {
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(DLT, 0));
        // A dead-lettered message still counts as consumed; the DLT is where it ended up.
        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            stats.deadLettered();
            stats.consumed();
            dlt.accept(record, ex);
        };
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3L));
        // Bad input is deterministic: never retry it, send it straight to the DLT.
        handler.addNotRetryableExceptions(InvalidClaimMessageException.class, ConstraintViolationException.class);
        return handler;
    }
}
