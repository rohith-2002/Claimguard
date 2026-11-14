package com.claimguard.screening;

import com.claimguard.api.ClaimRequest;
import java.time.Instant;

public interface ClaimHistoryStore {

    /** @return true if the claimId was newly recorded, false if it was already known (duplicate). */
    boolean record(ClaimRequest claim);

    /** Claims on the policy with reportedAt in [from, to), excluding excludeClaimId. */
    int countPolicyClaims(String policyNumber, Instant from, Instant to, String excludeClaimId);

    /** Claims on this vehicle under a DIFFERENT policy with reportedAt in [from, to). */
    int countVehicleClaimsOnOtherPolicies(String vehicleRegNo, String policyNumber, Instant from, Instant to);

    /** Claims for the provider with reportedAt in [from, to] (to inclusive), excluding excludeClaimId. */
    int countProviderClaims(String providerId, Instant from, Instant to, String excludeClaimId);
}
