package com.claimguard.service;

import com.claimguard.api.dto.ClaimRequest;
import com.claimguard.api.dto.ScreeningResponse;
import com.claimguard.config.ScreeningProperties;
import com.claimguard.domain.ClaimRecord;
import com.claimguard.domain.TriggeredRule;
import com.claimguard.domain.rules.FraudRule;
import com.claimguard.persistence.AlertEntity;
import com.claimguard.persistence.AlertRepository;
import com.claimguard.store.ClaimHistoryStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The single screening path. Idempotent per claimId:
 * - rules exclude the claim's own id when reading history, so a re-post scores identically;
 * - history.record() is a no-op for a known claimId;
 * - the alerts.claim_id unique constraint is the final guard against concurrent duplicates.
 * Intentionally not @Transactional: a unique-violation inside an outer transaction would poison it.
 */
@Service
public class ScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningService.class);

    private final List<FraudRule> rules;
    private final ScreeningProperties properties;
    private final ClaimHistoryStore history;
    private final AlertRepository alerts;
    private final Clock clock;

    public ScreeningService(List<FraudRule> rules, ScreeningProperties properties,
                            ClaimHistoryStore history, AlertRepository alerts, Clock clock) {
        this.rules = rules;
        this.properties = properties;
        this.history = history;
        this.alerts = alerts;
        this.clock = clock;
    }

    public ScreeningResponse screen(ClaimRequest claim) {
        ScreeningResponse result = evaluate(claim);
        history.record(new ClaimRecord(claim.claimId(), claim.policyNumber(), claim.vehicleRegNo(),
                claim.providerId(), claim.reportedAt()));
        if (result.alertRaised()) {
            persistAlertOnce(claim, result);
        }
        log.info("Screened claimId={} score={} alertRaised={}",
                claim.claimId(), result.score(), result.alertRaised());
        return result;
    }

    /** Pure scoring: runs every rule and sums weights. No persistence, no side effects. */
    private ScreeningResponse evaluate(ClaimRequest claim) {
        List<TriggeredRule> triggered = rules.stream()
                .map(rule -> rule.evaluate(claim))
                .flatMap(Optional::stream)
                .toList();
        int score = triggered.stream().mapToInt(TriggeredRule::weight).sum();
        return new ScreeningResponse(claim.claimId(), score, score >= properties.alertThreshold(), triggered);
    }

    private void persistAlertOnce(ClaimRequest claim, ScreeningResponse result) {
        if (alerts.existsByClaimId(claim.claimId())) {
            return;
        }
        try {
            alerts.save(AlertEntity.from(claim, result, Instant.now(clock)));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            log.info("Alert for claimId={} already created concurrently", claim.claimId());
        }
    }
}
