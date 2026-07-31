-- BE-7 (Fase 2) — recurring series (salary, rent, subscriptions). The schedule is explicit
-- (day_of_month / day_of_week / month_of_year), never derived from start_date (CT-1): changing
-- which day the rent is due must not move the date the series started on. Which field is required
-- depends on frequency; OpenAPI cannot express that conditional, so the application validates it
-- (VALIDATION_ERROR with fieldErrors) instead of a CHECK constraint here.

CREATE TABLE recurrence_rule (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID         NOT NULL REFERENCES workspace (id),
    type          VARCHAR(8)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    account_id    UUID         NOT NULL REFERENCES account (id),
    category_id   UUID         NOT NULL REFERENCES category (id),
    amount_cents  BIGINT       NOT NULL CHECK (amount_cents > 0),
    description   VARCHAR(255) NOT NULL,
    note          TEXT,
    frequency     VARCHAR(7)   NOT NULL CHECK (frequency IN ('MONTHLY', 'WEEKLY', 'YEARLY')),
    -- MONTHLY and YEARLY; a day the month does not have falls on the month's last day.
    day_of_month  SMALLINT     CHECK (day_of_month BETWEEN 1 AND 31),
    -- WEEKLY only.
    day_of_week   VARCHAR(9)
        CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    -- YEARLY only, paired with day_of_month.
    month_of_year SMALLINT     CHECK (month_of_year BETWEEN 1 AND 12),
    start_date    DATE         NOT NULL,
    -- Inclusive; NULL means open-ended. Set when a THIS_AND_FUTURE edit/delete closes the rule.
    end_date      DATE,
    -- A closed or user-deactivated rule stops producing new occurrences but keeps the ones
    -- already materialized (turning off a subscription does not undo the months already paid).
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ,
    CONSTRAINT ck_recurrence_rule_period CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX ix_recurrence_rule_workspace ON recurrence_rule (workspace_id);
CREATE INDEX ix_recurrence_rule_workspace_active ON recurrence_rule (workspace_id, active);

-- The rule that materialized this row, when it came from a recurrence (CT-1) — the extrato and
-- the calendar both use it to badge the entry and offer editing the whole series.
ALTER TABLE transaction ADD COLUMN recurrence_id UUID REFERENCES recurrence_rule (id);
CREATE INDEX ix_transaction_recurrence ON transaction (recurrence_id);
