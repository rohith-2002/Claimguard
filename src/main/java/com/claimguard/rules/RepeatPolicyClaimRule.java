package com.claimguard.rules;

import com.claimguard.api.ClaimRequest;
import com.claimguard.ScreeningProperties;
import com.claimguard.ScreeningProperties.RuleConfig;
import com.claimguard.screening.ClaimHistoryStore;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * N or more earlier claims (earlier reportedAt) on the same policy in the 180 days before this claim's
 * reportedAt. Window is [reportedAt - 180d, reportedAt). Inclusive threshold.
 */
@Component
public class RepeatPolicyClaimRule extends ConfiguredRule {

    static final Duration WINDOW = Duration.ofDays(180);

    private final ClaimHistoryStore history;

    public RepeatPolicyClaimRule(ScreeningProperties properties, ClaimHistoryStore history) {
        super(properties);
        this.history = history;
    }

    @Override
    public String code() {
        return "REPEAT_POLICY_CLAIM";
    }

    @Override
    protected Optional<String> reasonIfTriggered(ClaimRequest claim, RuleConfig config) {
        int earlier = history.countPolicyClaims(claim.policyNumber(),
                claim.reportedAt().minus(WINDOW), claim.reportedAt(), claim.claimId());
        if (earlier < config.threshold().intValue()) {
            return Optional.empty();
        }
        return Optional.of("%d earlier claims on policy %s in the last 180 days (limit %s)"
                .formatted(earlier, claim.policyNumber(), config.threshold().toPlainString()));
    }
}
