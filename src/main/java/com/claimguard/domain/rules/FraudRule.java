package com.claimguard.domain.rules;

import com.claimguard.api.dto.ClaimRequest;
import com.claimguard.domain.TriggeredRule;
import java.util.Optional;

public interface FraudRule {

    String code();

    /** @return the triggered rule with its reason, or empty when the claim passes this rule. */
    Optional<TriggeredRule> evaluate(ClaimRequest claim);
}
