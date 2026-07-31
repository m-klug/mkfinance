-- BE-7 (Fase 2) — one row per (rule, occurrence_date), unique. This is what makes materializing the
-- same competency twice a no-op, and lets an occurrence be marked SKIPPED ("pular esta ocorrência")
-- even when no transaction was ever generated for it.

CREATE TABLE recurrence_occurrence (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id         UUID        NOT NULL REFERENCES recurrence_rule (id),
    occurrence_date DATE        NOT NULL,
    status          VARCHAR(9)  NOT NULL CHECK (status IN ('GENERATED', 'SKIPPED')),
    -- NULL when SKIPPED without ever materializing, or after a THIS_ONE delete removes the PENDING row.
    transaction_id  UUID        REFERENCES transaction (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_recurrence_occurrence_rule_date UNIQUE (rule_id, occurrence_date)
);

CREATE INDEX ix_recurrence_occurrence_rule ON recurrence_occurrence (rule_id);
