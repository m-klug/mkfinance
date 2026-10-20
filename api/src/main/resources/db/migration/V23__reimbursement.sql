-- Reembolso (ADR-0002): parte de uma despesa paga pelo usuario e de outras pessoas, devolvida depois
-- por um INCOME registrado (PIX, dinheiro numa conta CASH...). O vinculo abate a despesa no mes de
-- origem e a renda do mes do recebimento; nenhum valor efetivo e materializado, sempre se calcula.

-- Quanto o usuario espera receber de volta de uma despesa. Teto da soma dos vinculos; so em EXPENSE.
ALTER TABLE transaction
    ADD COLUMN reimbursable_cents bigint,
    ADD CONSTRAINT ck_transaction_reimbursable CHECK (
        reimbursable_cents IS NULL
        OR (type = 'EXPENSE' AND reimbursable_cents > 0 AND reimbursable_cents <= amount_cents));

CREATE TABLE reimbursement (
    id                     uuid        PRIMARY KEY,
    workspace_id           uuid        NOT NULL REFERENCES workspace (id),
    income_transaction_id  uuid        NOT NULL REFERENCES transaction (id),
    expense_transaction_id uuid        NOT NULL REFERENCES transaction (id),
    amount_cents           bigint      NOT NULL CHECK (amount_cents > 0),
    created_at             timestamptz NOT NULL,
    CONSTRAINT uq_reimbursement_pair UNIQUE (income_transaction_id, expense_transaction_id),
    CONSTRAINT ck_reimbursement_distinct CHECK (income_transaction_id <> expense_transaction_id)
);

CREATE INDEX ix_reimbursement_income ON reimbursement (income_transaction_id);
CREATE INDEX ix_reimbursement_expense ON reimbursement (expense_transaction_id);
CREATE INDEX ix_reimbursement_workspace ON reimbursement (workspace_id);
