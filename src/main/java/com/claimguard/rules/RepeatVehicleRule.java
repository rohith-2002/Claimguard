package com.claimguard.rules;

import com.claimguard.ScreeningProperties;
import com.claimguard.ScreeningProperties.RuleConfig;
import com.claimguard.api.ClaimRequest;
import com.claimguard.screening.ClaimHistoryStore;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The same vehicle claimed under a DIFFERENT policy within the 90 days before this claim's reportedAt.
 * Window is [reportedAt - 90d, reportedAt). Inclusive threshold.
 */
@Component
public class RepeatVehicleRule extends ConfiguredRule {

    static final Duration WINDOW = Duration.ofDays(90);

    private final ClaimHistoryStore history;

    public RepeatVehicleRule(ScreeningProperties properties, ClaimHistoryStore history) {
        super(properties);
        this.history = history;
    }

    @Override
    public String code() {
        return "REPEAT_VEHICLE";
    }

    @Override
    protected Optional<String> reasonIfTriggered(ClaimRequest claim, RuleConfig config) {
        if (claim.vehicleRegNo() == null) {
            return Optional.empty();
        }
        int others = history.countVehicleClaimsOnOtherPolicies(claim.vehicleRegNo(), claim.policyNumber(),
                claim.reportedAt().minus(WINDOW), claim.reportedAt());
        if (others < config.threshold().intValue()) {
            return Optional.empty();
        }
        return Optional.of("Vehicle %s was claimed under %d other policies in the last 90 days (limit %s)"
                .formatted(claim.vehicleRegNo(), others, config.threshold().toPlainString()));
    }
}
