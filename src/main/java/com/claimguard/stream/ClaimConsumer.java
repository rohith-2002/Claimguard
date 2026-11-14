package com.claimguard.stream;

import com.claimguard.api.ClaimRequest;
import com.claimguard.screening.ScreeningService;
import com.claimguard.screening.StreamStats;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes claims from Kafka and runs them through the same {@link ScreeningService#screen} the HTTP API
 * uses. Bad messages are not retried; they are dead-lettered by the error handler in {@link KafkaConfig}.
 */
@Component
@ConditionalOnProperty(name = "claimguard.kafka.enabled", havingValue = "true")
class ClaimConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClaimConsumer.class);

    private final ObjectMapper mapper;
    private final Validator validator;
    private final ScreeningService screeningService;
    private final StreamStats stats;

    ClaimConsumer(ObjectMapper mapper, Validator validator, ScreeningService screeningService, StreamStats stats) {
        this.mapper = mapper;
        this.validator = validator;
        this.screeningService = screeningService;
        this.stats = stats;
    }

    @KafkaListener(topics = "claims.submitted")
    void onMessage(ConsumerRecord<String, String> record) {
        ClaimRequest claim = parse(record);
        Set<ConstraintViolation<ClaimRequest>> violations = validator.validate(claim);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        screeningService.screen(claim);
        stats.consumed();
    }

    private ClaimRequest parse(ConsumerRecord<String, String> record) {
        if (record.value() == null) {
            throw new InvalidClaimMessageException(record.topic(), record.partition(), record.offset());
        }
        try {
            return mapper.readValue(record.value(), ClaimRequest.class);
        } catch (JsonProcessingException e) {
            log.warn("Dead-lettering unparseable claim at {}-{}@{}", record.topic(), record.partition(),
                    record.offset());
            // No cause: a Jackson message can contain a snippet of the payload (the mobile number).
            throw new InvalidClaimMessageException(record.topic(), record.partition(), record.offset());
        }
    }
}
