CREATE TABLE alerts (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    claim_id               VARCHAR(64)  NOT NULL,
    policy_number          VARCHAR(64)  NOT NULL,
    line_of_business       VARCHAR(16)  NOT NULL,
    claimant_mobile_masked VARCHAR(20)  NOT NULL,
    score                  INT          NOT NULL,
    status                 VARCHAR(24)  NOT NULL DEFAULT 'OPEN',
    reported_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_alerts_claim_id UNIQUE (claim_id)
);

CREATE TABLE alert_rules (
    alert_id BIGINT       NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    code     VARCHAR(40)  NOT NULL,
    weight   INT          NOT NULL,
    reason   VARCHAR(500) NOT NULL
);
CREATE INDEX idx_alert_rules_alert ON alert_rules (alert_id);

-- Serves: WHERE status = ? AND line_of_business = ? AND score >= ?
--         ORDER BY score DESC, reported_at DESC, id DESC
CREATE INDEX idx_alerts_queue ON alerts (status, line_of_business, score DESC, reported_at DESC, id DESC);
