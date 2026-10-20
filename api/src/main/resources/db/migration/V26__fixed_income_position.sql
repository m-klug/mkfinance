-- Posicao de renda fixa: um titulo com taxa e vencimento, detalhando o saldo de uma conta de
-- investimento (nao soma a ele, nao move dinheiro). Duas origens:
--   * vinculada a um aporte: contribution_transaction_id aponta a perna IN da transferencia de compra;
--     conta, valor, data e caixinha sao copiados dela no cadastro;
--   * manual (titulo anterior ao app): contribution_transaction_id nulo; conta, valor e data sao os
--     informados.
-- Conta, valor, data e caixinha ficam sempre na propria posicao (um caminho so para lista, estimativas
-- e resumo); a guarda sobre a transferencia vinculada impede que as duas divirjam.
-- As colunas de baixa (settled_*, received_cents, ...) sao preenchidas quando o titulo e pago.

CREATE TABLE fixed_income_position (
    id                            uuid          PRIMARY KEY,
    workspace_id                  uuid          NOT NULL REFERENCES workspace (id),
    account_id                    uuid          NOT NULL REFERENCES account (id),
    vault_id                      uuid          REFERENCES vault (id),
    contribution_transaction_id   uuid          REFERENCES transaction (id),
    principal_cents               bigint        NOT NULL CHECK (principal_cents > 0),
    purchase_date                 date          NOT NULL,
    name                          varchar(80),
    type                          varchar(24)   NOT NULL
        CHECK (type IN ('CDB', 'LC', 'LCI', 'LCA', 'TESOURO_SELIC', 'TESOURO_PREFIXADO', 'TESOURO_IPCA')),
    indexer                       varchar(16)   NOT NULL
        CHECK (indexer IN ('PREFIXED', 'CDI_PERCENT', 'SELIC_PERCENT', 'IPCA_PLUS')),
    rate                          numeric(9, 4) NOT NULL CHECK (rate >= 0),
    maturity_date                 date          NOT NULL,
    status                        varchar(8)    NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SETTLED')),
    settled_date                  date,
    received_cents                bigint,
    estimated_net_cents           bigint,
    yield_cents                   bigint,
    yield_transaction_id          uuid          REFERENCES transaction (id),
    return_transfer_transaction_id uuid         REFERENCES transaction (id),
    created_at                    timestamptz   NOT NULL,
    updated_at                    timestamptz   NOT NULL,
    CONSTRAINT ck_position_dates CHECK (maturity_date > purchase_date),
    CONSTRAINT ck_position_settled CHECK (
        (status = 'ACTIVE' AND settled_date IS NULL AND received_cents IS NULL)
        OR (status = 'SETTLED' AND settled_date IS NOT NULL AND received_cents IS NOT NULL))
);

-- Um aporte so pode originar uma posicao; um lancamento so pode ser rendimento / retorno de uma.
CREATE UNIQUE INDEX uq_position_contribution ON fixed_income_position (contribution_transaction_id)
    WHERE contribution_transaction_id IS NOT NULL;
CREATE UNIQUE INDEX uq_position_yield ON fixed_income_position (yield_transaction_id)
    WHERE yield_transaction_id IS NOT NULL;
CREATE UNIQUE INDEX uq_position_return_transfer ON fixed_income_position (return_transfer_transaction_id)
    WHERE return_transfer_transaction_id IS NOT NULL;

CREATE INDEX ix_position_workspace ON fixed_income_position (workspace_id);
CREATE INDEX ix_position_account ON fixed_income_position (account_id);
CREATE INDEX ix_position_vault ON fixed_income_position (vault_id) WHERE vault_id IS NOT NULL;
