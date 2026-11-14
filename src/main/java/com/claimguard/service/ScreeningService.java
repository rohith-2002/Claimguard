package com.claimguard.service;

import com.claimguard.api.dto.ClaimRequest;
import com.claimguard.api.dto.ScreeningResponse;
import com.claimguard.domain.ClaimRecord;
import com.claimguard.persistence.AlertEntity;
import com.claimguard.persistence.AlertRepository;
import com.claimguard.store.ClaimHistoryStore;
import java.time.Clock;
import java.time.Instant;
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

    private final ScreeningEngine engine;
    private final ClaimHistoryStore history;
    private final AlertRepository alerts;
    private final Clock clock;

    public ScreeningService(ScreeningEngine engine, ClaimHistoryStore history,
                            AlertRepository alerts, Clock clock) {
        this.engine = engine;
        this.history = history;
        this.alerts = alerts;
        this.clock = clock;
    }

    public ScreeningResponse screen(ClaimRequest claim) {
        ScreeningResponse result = engine.evaluate(claim);
        history.record(new ClaimRecord(claim.claimId(), claim.policyNumber(), claim.vehicleRegNo(),
                claim.providerId(), claim.reportedAt()));
        if (result.alertRaised()) {
            persistAlertOnce(claim, result);
        }
        log.info("Screened claimId={} score={} alertRaised={}",
                claim.claimId(), result.score(), result.alertRaised());
        return result;
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
