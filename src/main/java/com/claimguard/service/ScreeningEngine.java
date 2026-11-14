package com.claimguard.service;

import com.claimguard.api.dto.ClaimRequest;
import com.claimguard.api.dto.ScreeningResponse;
import com.claimguard.config.ScreeningProperties;
import com.claimguard.domain.TriggeredRule;
import com.claimguard.domain.rules.FraudRule;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Pure scoring: runs every rule and sums weights. No persistence, no side effects. */
@Component
public class ScreeningEngine {

    private final List<FraudRule> rules;
    private final ScreeningProperties properties;

    public ScreeningEngine(List<FraudRule> rules, ScreeningProperties properties) {
        this.rules = rules;
        this.properties = properties;
    }

    public ScreeningResponse evaluate(ClaimRequest claim) {
        List<TriggeredRule> triggered = rules.stream()
                .map(rule -> rule.evaluate(claim))
                .flatMap(Optional::stream)
                .toList();
        int score = triggered.stream().mapToInt(TriggeredRule::weight).sum();
        return new ScreeningResponse(claim.claimId(), score, score >= properties.alertThreshold(), triggered);
    }
}
