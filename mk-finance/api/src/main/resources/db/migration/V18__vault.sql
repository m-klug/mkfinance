-- BE-9 (Caixinhas) — a vault is a virtual subdivision of an INVESTMENT/SAVINGS account's balance.
-- The account balance stays a single amount derived from `transaction`; a vault never holds money
-- of its own (DESENVOLVIMENTO-CAIXINHAS.md §4.1). Optional per account: nothing migrates existing
-- accounts, and no vault is created automatically.

CREATE TABLE vault (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID         NOT NULL REFERENCES workspace (id),
    account_id          UUID         NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    name                VARCHAR(120) NOT NULL,
    contribution_pillar VARCHAR(20)  NOT NULL
        CHECK (contribution_pillar IN ('GOALS', 'FINANCIAL_FREEDOM')),
    target_amount_cents BIGINT       CHECK (target_amount_cents > 0),
    target_date         DATE,
    color               VARCHAR(7),
    archived            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    -- valor-alvo sem prazo é válido; prazo sem valor-alvo não tem do que calcular progresso
    CONSTRAINT ck_vault_goal CHECK (target_date IS NULL OR target_amount_cents IS NOT NULL)
);
CREATE INDEX ix_vault_workspace ON vault (workspace_id);
CREATE INDEX ix_vault_account   ON vault (account_id);
-- único por CONTA, não por workspace: "Reserva" pode existir na corretora e na poupança
CREATE UNIQUE INDEX ux_vault_account_name_ci ON vault (account_id, lower(name));
