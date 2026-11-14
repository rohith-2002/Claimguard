package com.claimguard;

import com.claimguard.api.dto.ClaimRequest;
import com.claimguard.config.ScreeningProperties;
import com.claimguard.config.ScreeningProperties.RuleConfig;
import com.claimguard.domain.LineOfBusiness;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/** Shared test data. The baseline claim triggers no rule; tweak one field to trigger one. */
public final class Fixtures {

    private Fixtures() { }

    public static ScreeningProperties props() {
        return props("30");
    }

    /** Defaults from application.yml, with a custom EARLY_CLAIM threshold. */
    public static ScreeningProperties props(String earlyThreshold) {
        return new ScreeningProperties(50, Map.of(
                "EARLY_CLAIM", new RuleConfig(true, new BigDecimal(earlyThreshold), 30),
                "HIGH_AMOUNT_RATIO", new RuleConfig(true, new BigDecimal("0.8"), 35),
                "LATE_REPORTING", new RuleConfig(true, new BigDecimal("15"), 20),
                "REPEAT_POLICY_CLAIM", new RuleConfig(true, new BigDecimal("2"), 25)));
    }

    public static ScreeningProperties propsWithEarlyDisabled() {
        return new ScreeningProperties(50, Map.of(
                "EARLY_CLAIM", new RuleConfig(false, new BigDecimal("30"), 30)));
    }

    public static Builder claim(String claimId) {
        return new Builder(claimId);
    }

    /** Triggers EARLY_CLAIM (30) + HIGH_AMOUNT_RATIO (35) + LATE_REPORTING (20) = 85. */
    public static Builder highScoring(String claimId) {
        return claim(claimId)
                .policyStart(LocalDate.of(2026, 1, 1))
                .loss(LocalDate.of(2026, 1, 7))
                .reportedAt(Instant.parse("2026-02-01T10:00:00Z"))
                .sumInsured("500000")
                .claimed("450000");
    }

    public static final class Builder {
        private final String claimId;
        private String policy;
        private LocalDate policyStart = LocalDate.of(2025, 1, 1);
        private BigDecimal sumInsured = new BigDecimal("1000000");
        private LineOfBusiness lob = LineOfBusiness.MOTOR;
        private String vehicle = "KA01AB1234";
        private String provider = "GAR-1";
        private String mobile = "9876543210";
        private LocalDate loss = LocalDate.of(2026, 1, 10);
        private Instant reportedAt = Instant.parse("2026-01-11T10:00:00Z");
        private BigDecimal claimed = new BigDecimal("100000");

        private Builder(String claimId) {
            this.claimId = claimId;
            this.policy = "POL-" + claimId;
        }

        public Builder policy(String v) { this.policy = v; return this; }
        public Builder policyStart(LocalDate v) { this.policyStart = v; return this; }
        public Builder sumInsured(String v) { this.sumInsured = new BigDecimal(v); return this; }
        public Builder lob(LineOfBusiness v) { this.lob = v; return this; }
        public Builder mobile(String v) { this.mobile = v; return this; }
        public Builder loss(LocalDate v) { this.loss = v; return this; }
        public Builder reportedAt(Instant v) { this.reportedAt = v; return this; }
        public Builder claimed(String v) { this.claimed = new BigDecimal(v); return this; }

        public ClaimRequest build() {
            String vehicleReg = lob == LineOfBusiness.MOTOR ? vehicle : null;
            return new ClaimRequest(claimId, policy, policyStart, sumInsured, lob, vehicleReg,
                    provider, mobile, loss, reportedAt, claimed);
        }
    }
}
