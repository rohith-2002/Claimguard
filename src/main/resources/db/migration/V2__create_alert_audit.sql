-- Append-only investigator decisions. Rows are inserted, never updated or deleted.
CREATE TABLE alert_audit (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alert_id     BIGINT        NOT NULL REFERENCES alerts (id) ON DELETE CASCADE,
    investigator VARCHAR(120)  NOT NULL,
    changed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    from_status  VARCHAR(24)   NOT NULL,
    to_status    VARCHAR(24)   NOT NULL,
    note         VARCHAR(1000) NOT NULL
);

CREATE INDEX idx_alert_audit_alert ON alert_audit (alert_id);
