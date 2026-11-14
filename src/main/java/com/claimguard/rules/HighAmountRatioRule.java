package com.claimguard.rules;

import com.claimguard.api.ClaimRequest;
import com.claimguard.ScreeningProperties;
import com.claimguard.ScreeningProperties.RuleConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Claimed amount >= R x sum insured (inclusive). Compared by multiplication so the boundary is exact. */
@Component
public class HighAmountRatioRule extends ConfiguredRule {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public HighAmountRatioRule(ScreeningProperties properties) {
        super(properties);
    }

    @Override
    public String code() {
        return "HIGH_AMOUNT_RATIO";
    }

    @Override
    protected Optional<String> reasonIfTriggered(ClaimRequest claim, RuleConfig config) {
        BigDecimal limitAmount = claim.sumInsured().multiply(config.threshold());
        if (claim.claimedAmount().compareTo(limitAmount) < 0) {
            return Optional.empty();
        }
        BigDecimal pct = claim.claimedAmount().multiply(HUNDRED)
                .divide(claim.sumInsured(), 1, RoundingMode.HALF_UP);
        String limitPct = config.threshold().multiply(HUNDRED).stripTrailingZeros().toPlainString();
        return Optional.of("Claimed amount is %s%% of sum insured (limit %s%%)"
                .formatted(pct.toPlainString(), limitPct));
    }
}
