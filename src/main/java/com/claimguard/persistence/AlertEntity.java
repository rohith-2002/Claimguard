package com.claimguard.persistence;

import com.claimguard.api.dto.ClaimRequest;
import com.claimguard.api.dto.ScreeningResponse;
import com.claimguard.domain.AlertStatus;
import com.claimguard.domain.LineOfBusiness;
import com.claimguard.util.Masking;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "alerts")
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false, unique = true, updatable = false)
    private String claimId;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_of_business", nullable = false)
    private LineOfBusiness lineOfBusiness;

    /** Only the masked number is persisted. */
    @Column(name = "claimant_mobile_masked", nullable = false)
    private String claimantMobileMasked;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "alert_rules", joinColumns = @JoinColumn(name = "alert_id"))
    private List<TriggeredRuleRow> triggeredRules = new ArrayList<>();

    protected AlertEntity() { }

    public static AlertEntity from(ClaimRequest claim, ScreeningResponse result, Instant now) {
        AlertEntity a = new AlertEntity();
        a.claimId = claim.claimId();
        a.policyNumber = claim.policyNumber();
        a.lineOfBusiness = claim.lineOfBusiness();
        a.claimantMobileMasked = Masking.mask(claim.claimantMobile());
        a.score = result.score();
        a.reportedAt = claim.reportedAt();
        a.createdAt = now;
        result.triggeredRules().forEach(r ->
                a.triggeredRules.add(new TriggeredRuleRow(r.code(), r.weight(), r.reason())));
        return a;
    }

    public Long getId() { return id; }

    public String getClaimId() { return claimId; }

    public String getPolicyNumber() { return policyNumber; }

    public LineOfBusiness getLineOfBusiness() { return lineOfBusiness; }

    public String getClaimantMobileMasked() { return claimantMobileMasked; }

    public int getScore() { return score; }

    public AlertStatus getStatus() { return status; }

    public Instant getReportedAt() { return reportedAt; }

    public Instant getCreatedAt() { return createdAt; }

    public List<TriggeredRuleRow> getTriggeredRules() { return triggeredRules; }
}
