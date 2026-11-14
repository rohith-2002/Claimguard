package com.claimguard.screening;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimguard.api.ClaimRequest;
import com.claimguard.api.ScreeningResponse;
import com.claimguard.screening.ScreeningService;
import com.claimguard.screening.ClaimDataGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Screens the whole data/claims.jsonl in file order and prints hits per rule vs injected patterns. */
@SpringBootTest
@ActiveProfiles("test")
class DatasetScreeningTest {

    private static final Path FILE = Path.of("data/claims.jsonl");
    private static final List<String> TASK1_RULES =
            List.of("EARLY_CLAIM", "HIGH_AMOUNT_RATIO", "LATE_REPORTING", "REPEAT_POLICY_CLAIM");

    @Autowired ScreeningService service;
    @Autowired ObjectMapper mapper;

    @Test
    void screenWholeFileAndPrintHitsPerRule() throws Exception {
        if (!Files.exists(FILE)) {
            ClaimDataGenerator.generate(FILE);
        }
        Map<String, Integer> hits = new TreeMap<>();
        Map<String, Integer> injected = new TreeMap<>();
        Map<String, Integer> injectedAndHit = new TreeMap<>();
        int claims = 0;
        int alerts = 0;

        for (String line : Files.readAllLines(FILE)) {
            JsonNode node = mapper.readTree(line);
            ScreeningResponse result = service.screen(mapper.treeToValue(node, ClaimRequest.class));
            claims++;
            if (result.alertRaised()) {
                alerts++;
            }
            result.triggeredRules().forEach(r -> hits.merge(r.code(), 1, Integer::sum));
            for (JsonNode label : node.get("label")) {
                injected.merge(label.asText(), 1, Integer::sum);
                boolean hit = result.triggeredRules().stream().anyMatch(r -> r.code().equals(label.asText()));
                if (hit) {
                    injectedAndHit.merge(label.asText(), 1, Integer::sum);
                }
            }
        }

        System.out.printf("%nclaims screened: %d, alerts raised: %d%n", claims, alerts);
        System.out.printf("%-22s %6s %9s %14s%n", "rule", "hits", "injected", "injected&hit");
        for (String rule : TASK1_RULES) {
            System.out.printf("%-22s %6d %9d %14d%n", rule, hits.getOrDefault(rule, 0),
                    injected.getOrDefault(rule, 0), injectedAndHit.getOrDefault(rule, 0));
        }
        System.out.printf("(Task 2 patterns injected: REPEAT_VEHICLE=%d, PROVIDER_SPIKE=%d)%n",
                injected.getOrDefault("REPEAT_VEHICLE", 0), injected.getOrDefault("PROVIDER_SPIKE", 0));

        assertThat(claims).isGreaterThanOrEqualTo(500);
        TASK1_RULES.forEach(rule -> assertThat(injectedAndHit.getOrDefault(rule, 0))
                .as("every injected %s claim should be detected", rule)
                .isEqualTo(injected.getOrDefault(rule, 0)));
    }
}
