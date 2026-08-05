-- BE-9 (Caixinhas) — the vault's single ledger (DESENVOLVIMENTO-CAIXINHAS.md §4.1). Three sources
-- of movement (aporte/resgate, remanejo, rendimento) don't fit a single `vault_id` column on
-- `transaction`: a contribution/withdrawal is 1:1 with a transaction, a reallocation is 0 transactions
-- (0:2), and a yield is 1 transaction for N vaults (1:N). This table resolves all three while keeping
-- the vault balance derived by aggregation, never materialized.
--
-- `amount_cents` NULL on CONTRIBUTION/WITHDRAWAL means "mirror the transaction's signed amount" —
-- TransferService can freely change amount/date/status without ever touching this row, and deleting
-- the transaction cleans its ledger line via ON DELETE CASCADE (same discipline as V17's
-- transaction_tag). Only REALLOCATION and YIELD carry their own value, since neither has a 1:1
-- transaction to mirror.

CREATE TABLE vault_entry (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspace (id),
    vault_id        UUID        NOT NULL REFERENCES vault (id) ON DELETE CASCADE,
    source          VARCHAR(12) NOT NULL
        CHECK (source IN ('CONTRIBUTION','WITHDRAWAL','YIELD','REALLOCATION')),
    transaction_id  UUID        REFERENCES transaction (id) ON DELETE CASCADE,
    amount_cents    BIGINT      CHECK (amount_cents <> 0),  -- NULL = espelha a transação
    reallocation_id UUID,                                    -- agrupa as 2 pernas do remanejo
    date            DATE,                                    -- só REALLOCATION
    description     VARCHAR(255),                            -- só REALLOCATION
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_vault_entry_source CHECK (
        CASE source
            WHEN 'REALLOCATION' THEN transaction_id IS NULL AND amount_cents IS NOT NULL
                                 AND reallocation_id IS NOT NULL AND date IS NOT NULL
            WHEN 'YIELD'        THEN transaction_id IS NOT NULL AND amount_cents IS NOT NULL
                                 AND reallocation_id IS NULL AND date IS NULL
            ELSE                     transaction_id IS NOT NULL AND amount_cents IS NULL
                                 AND reallocation_id IS NULL AND date IS NULL
        END
    )
);
CREATE INDEX ix_vault_entry_workspace    ON vault_entry (workspace_id);
CREATE INDEX ix_vault_entry_vault        ON vault_entry (vault_id);
CREATE INDEX ix_vault_entry_transaction  ON vault_entry (transaction_id);
CREATE INDEX ix_vault_entry_reallocation ON vault_entry (reallocation_id);
-- uma transferência tem no máximo UMA caixinha por perna; parcial porque rendimento é N linhas
CREATE UNIQUE INDEX ux_vault_entry_mirror ON vault_entry (transaction_id) WHERE amount_cents IS NULL;
