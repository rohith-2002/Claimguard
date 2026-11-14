package com.claimguard.screening;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Publishes data/claims.jsonl to the claims.submitted topic, in file order, keyed by policyNumber.
 *
 * <pre>mvn -q test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.claimguard.screening.ClaimReplay -Dexec.args="data/claims.jsonl --with-bad"</pre>
 *
 * The optional second argument "--with-bad" first sends two malformed records so the dead-letter path can
 * be observed. Because the dataset is ordered by reportedAt and the topic has one partition, each key lands
 * in order, which is what the Redis sliding window expects.
 */
public final class ClaimReplay {

    private static final String TOPIC = "claims.submitted";

    private ClaimReplay() { }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args.length > 0 ? args[0] : "data/claims.jsonl");
        boolean withBad = args.length > 1 && args[1].equals("--with-bad");
        ObjectMapper mapper = new ObjectMapper();

        Properties props = new Properties();
        props.put("bootstrap.servers", System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9092"));
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        props.put("acks", "all");
        props.put("enable.idempotence", "true");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            if (withBad) {
                producer.send(new ProducerRecord<>(TOPIC, "bad", "this is not json"));
                producer.send(new ProducerRecord<>(TOPIC, "bad2", "{\"claimId\":\"\"}"));
            }
            int published = 0;
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) {
                    continue;
                }
                String policy = mapper.readTree(line).get("policyNumber").asText();
                producer.send(new ProducerRecord<>(TOPIC, policy, line));
                published++;
            }
            producer.flush();
            System.out.println("Published " + published + " claims" + (withBad ? " plus 2 bad records" : ""));
        }
    }
}
