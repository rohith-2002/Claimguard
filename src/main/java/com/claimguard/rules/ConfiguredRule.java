package com.claimguard.rules;

import com.claimguard.api.ClaimRequest;
import com.claimguard.ScreeningProperties;
import com.claimguard.ScreeningProperties.RuleConfig;
import com.claimguard.rules.TriggeredRule;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/** Looks up enabled/threshold/weight under screening.rules.&lt;CODE&gt; and builds the TriggeredRule. */
public abstract class ConfiguredRule implements FraudRule {

    private final ScreeningProperties properties;

    protected ConfiguredRule(ScreeningProperties properties) {
        this.properties = properties;
    }

    @Override
    public final Optional<TriggeredRule> evaluate(ClaimRequest claim) {
        RuleConfig config = properties.rules().get(code());
        if (config == null || !config.enabled()) {
            return Optional.empty();
        }
        return reasonIfTriggered(claim, config)
                .map(reason -> new TriggeredRule(code(), config.weight(), reason));
    }

    /** @return the human-readable reason when the rule fires, otherwise empty. */
    protected abstract Optional<String> reasonIfTriggered(ClaimRequest claim, RuleConfig config);

    /** Event-time calendar date (UTC) of the claim report. */
    protected static LocalDate reportedDate(ClaimRequest claim) {
        return claim.reportedAt().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
