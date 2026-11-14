package com.claimguard.domain.rules;

import com.claimguard.api.dto.ClaimRequest;
import com.claimguard.config.ScreeningProperties;
import com.claimguard.config.ScreeningProperties.RuleConfig;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Loss within N days of policy start (inclusive: exactly N days fires). */
@Component
public class EarlyClaimRule extends ConfiguredRule {

    public EarlyClaimRule(ScreeningProperties properties) {
        super(properties);
    }

    @Override
    public String code() {
        return "EARLY_CLAIM";
    }

    @Override
    protected Optional<String> reasonIfTriggered(ClaimRequest claim, RuleConfig config) {
        long days = ChronoUnit.DAYS.between(claim.policyStartDate(), claim.lossDate());
        if (days > config.threshold().longValue()) {
            return Optional.empty();
        }
        return Optional.of("Loss occurred %d days after policy start (limit %s)"
                .formatted(days, config.threshold().toPlainString()));
    }
}
