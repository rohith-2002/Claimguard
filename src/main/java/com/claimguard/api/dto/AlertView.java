package com.claimguard.api.dto;

import com.claimguard.domain.AlertStatus;
import com.claimguard.domain.LineOfBusiness;
import com.claimguard.domain.TriggeredRule;
import com.claimguard.persistence.AlertEntity;
import java.time.Instant;
import java.util.List;

/** claimantMobile is always the masked value; the full number is never stored. */
public record AlertView(Long id, String claimId, String policyNumber, LineOfBusiness lineOfBusiness,
                        String claimantMobile, int score, AlertStatus status, Instant reportedAt,
                        List<TriggeredRule> triggeredRules) {

    public static AlertView from(AlertEntity a) {
        return new AlertView(a.getId(), a.getClaimId(), a.getPolicyNumber(), a.getLineOfBusiness(),
                a.getClaimantMobileMasked(), a.getScore(), a.getStatus(), a.getReportedAt(),
                a.getTriggeredRules());
    }
}
