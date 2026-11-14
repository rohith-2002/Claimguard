package com.claimguard.domain.rules;

import com.claimguard.api.dto.ClaimRequest;
import com.claimguard.config.ScreeningProperties;
import com.claimguard.config.ScreeningProperties.RuleConfig;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Reported MORE than N days after loss (strict: exactly N days does not fire). */
@Component
public class LateReportingRule extends ConfiguredRule {

    public LateReportingRule(ScreeningProperties properties) {
        super(properties);
    }

    @Override
    public String code() {
        return "LATE_REPORTING";
    }

    @Override
    protected Optional<String> reasonIfTriggered(ClaimRequest claim, RuleConfig config) {
        long days = ChronoUnit.DAYS.between(claim.lossDate(), reportedDate(claim));
        if (days <= config.threshold().longValue()) {
            return Optional.empty();
        }
        return Optional.of("Claim reported %d days after loss (limit %s)"
                .formatted(days, config.threshold().toPlainString()));
    }
}
