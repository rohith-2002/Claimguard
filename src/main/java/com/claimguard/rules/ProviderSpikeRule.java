package com.claimguard.rules;

import com.claimguard.ScreeningProperties;
import com.claimguard.ScreeningProperties.RuleConfig;
import com.claimguard.api.ClaimRequest;
import com.claimguard.screening.ClaimHistoryStore;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * More than N claims for the same provider in the 7 days up to and including this claim's reportedAt.
 * Window is [reportedAt - 7d, reportedAt] (both ends inclusive); strict threshold (total must exceed N).
 */
@Component
public class ProviderSpikeRule extends ConfiguredRule {

    static final Duration WINDOW = Duration.ofDays(7);

    private final ClaimHistoryStore history;

    public ProviderSpikeRule(ScreeningProperties properties, ClaimHistoryStore history) {
        super(properties);
        this.history = history;
    }

    @Override
    public String code() {
        return "PROVIDER_SPIKE";
    }

    @Override
    protected Optional<String> reasonIfTriggered(ClaimRequest claim, RuleConfig config) {
        int total = 1 + history.countProviderClaims(claim.providerId(),
                claim.reportedAt().minus(WINDOW), claim.reportedAt(), claim.claimId());
        if (total <= config.threshold().intValue()) {
            return Optional.empty();
        }
        return Optional.of("Provider %s has %d claims in the last 7 days (limit %s)"
                .formatted(claim.providerId(), total, config.threshold().toPlainString()));
    }
}
