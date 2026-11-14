package com.claimguard.screening;

import com.claimguard.api.ClaimRequest;
import com.claimguard.api.ScreeningResponse;
import com.claimguard.ScreeningProperties;
import com.claimguard.rules.TriggeredRule;
import com.claimguard.rules.FraudRule;
import com.claimguard.alerts.AlertEntity;
import com.claimguard.alerts.AlertRepository;
import com.claimguard.screening.ClaimHistoryStore;
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

    public ScreeningService(List<FraudRule> rules, ScreeningProperties properties,
                            ClaimHistoryStore history, AlertRepository alerts) {
        this.rules = rules;
        this.properties = properties;
        this.history = history;
        this.alerts = alerts;
    }

    public ScreeningResponse screen(ClaimRequest claim) {
        ScreeningResponse result = evaluate(claim);
        history.record(claim);
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
            alerts.save(AlertEntity.from(claim, result, Instant.now()));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            log.info("Alert for claimId={} already created concurrently", claim.claimId());
        }
    }
}
