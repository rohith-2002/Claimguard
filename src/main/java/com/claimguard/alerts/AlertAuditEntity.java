package com.claimguard.alerts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row per investigator decision: who, when, from, to, note. Append-only. */
@Entity
@Table(name = "alert_audit")
public class AlertAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false, updatable = false)
    private Long alertId;

    @Column(nullable = false, updatable = false)
    private String investigator;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false)
    private AlertStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private AlertStatus toStatus;

    @Column(nullable = false, updatable = false)
    private String note;

    protected AlertAuditEntity() { }

    public static AlertAuditEntity of(Long alertId, String investigator, Instant changedAt,
                                      AlertStatus fromStatus, AlertStatus toStatus, String note) {
        AlertAuditEntity a = new AlertAuditEntity();
        a.alertId = alertId;
        a.investigator = investigator;
        a.changedAt = changedAt;
        a.fromStatus = fromStatus;
        a.toStatus = toStatus;
        a.note = note;
        return a;
    }

    public Long getId() { return id; }

    public Long getAlertId() { return alertId; }

    public String getInvestigator() { return investigator; }

    public Instant getChangedAt() { return changedAt; }

    public AlertStatus getFromStatus() { return fromStatus; }

    public AlertStatus getToStatus() { return toStatus; }

    public String getNote() { return note; }
}
