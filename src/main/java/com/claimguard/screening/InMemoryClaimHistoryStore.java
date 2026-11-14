package com.claimguard.screening;

import com.claimguard.api.ClaimRequest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "claimguard.history.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryClaimHistoryStore implements ClaimHistoryStore {

    private record VehicleClaim(String policyNumber, Instant reportedAt) { }

    /** policyNumber -> (claimId -> reportedAt). Keying by claimId makes record() idempotent. */
    private final Map<String, Map<String, Instant>> byPolicy = new ConcurrentHashMap<>();
    private final Map<String, Map<String, VehicleClaim>> byVehicle = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Instant>> byProvider = new ConcurrentHashMap<>();

    @Override
    public boolean record(ClaimRequest c) {
        boolean isNew = byPolicy.computeIfAbsent(c.policyNumber(), k -> new ConcurrentHashMap<>())
                .putIfAbsent(c.claimId(), c.reportedAt()) == null;
        if (c.vehicleRegNo() != null) {
            byVehicle.computeIfAbsent(c.vehicleRegNo(), k -> new ConcurrentHashMap<>())
                    .putIfAbsent(c.claimId(), new VehicleClaim(c.policyNumber(), c.reportedAt()));
        }
        byProvider.computeIfAbsent(c.providerId(), k -> new ConcurrentHashMap<>())
                .putIfAbsent(c.claimId(), c.reportedAt());
        return isNew;
    }

    @Override
    public int countPolicyClaims(String policyNumber, Instant from, Instant to, String excludeClaimId) {
        return (int) byPolicy.getOrDefault(policyNumber, Map.of()).entrySet().stream()
                .filter(e -> !e.getKey().equals(excludeClaimId))
                .filter(e -> !e.getValue().isBefore(from) && e.getValue().isBefore(to))
                .count();
    }

    @Override
    public int countVehicleClaimsOnOtherPolicies(String vehicle, String policy, Instant from, Instant to) {
        return (int) byVehicle.getOrDefault(vehicle, Map.of()).values().stream()
                .filter(v -> !v.policyNumber().equals(policy))
                .filter(v -> !v.reportedAt().isBefore(from) && v.reportedAt().isBefore(to))
                .count();
    }

    @Override
    public int countProviderClaims(String provider, Instant from, Instant to, String excludeClaimId) {
        return (int) byProvider.getOrDefault(provider, Map.of()).entrySet().stream()
                .filter(e -> !e.getKey().equals(excludeClaimId))
                .filter(e -> !e.getValue().isBefore(from) && !e.getValue().isAfter(to))
                .count();
    }
}
