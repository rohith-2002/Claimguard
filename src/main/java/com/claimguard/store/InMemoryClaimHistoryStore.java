package com.claimguard.store;

import com.claimguard.domain.ClaimRecord;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryClaimHistoryStore implements ClaimHistoryStore {

    /** policyNumber -> (claimId -> reportedAt). Keying by claimId makes record() idempotent. */
    private final Map<String, Map<String, Instant>> byPolicy = new ConcurrentHashMap<>();

    @Override
    public int countPolicyClaims(String policyNumber, Instant from, Instant to, String excludeClaimId) {
        return (int) byPolicy.getOrDefault(policyNumber, Map.of()).entrySet().stream()
                .filter(e -> !e.getKey().equals(excludeClaimId))
                .filter(e -> !e.getValue().isBefore(from) && e.getValue().isBefore(to))
                .count();
    }

    @Override
    public void record(ClaimRecord claim) {
        byPolicy.computeIfAbsent(claim.policyNumber(), k -> new ConcurrentHashMap<>())
                .putIfAbsent(claim.claimId(), claim.reportedAt());
    }
}
