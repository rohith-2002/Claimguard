package com.claimguard.store;

import com.claimguard.api.dto.ClaimRequest;
import java.time.Instant;

public interface ClaimHistoryStore {

    /** Claims on the policy with reportedAt in [from, to), ignoring excludeClaimId. */
    int countPolicyClaims(String policyNumber, Instant from, Instant to, String excludeClaimId);

    /** Idempotent: recording the same claimId again must not change any count. */
    void record(ClaimRequest claim);
}
