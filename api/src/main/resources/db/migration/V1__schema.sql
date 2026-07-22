-- MK Finance MVP schema. Money is always BIGINT cents. Business dates are DATE
-- (America/Sao_Paulo); TIMESTAMPTZ only for audit columns. See PLANEJAMENTO.md §4/§5.

CREATE TABLE app_user (
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name          VARCHAR(120)         NOT NULL,
    email         VARCHAR(255)         NOT NULL UNIQUE,
    password_hash VARCHAR(100)         NOT NULL,
    locale        VARCHAR(10)          NOT NULL DEFAULT 'pt-BR',
    created_at    TIMESTAMPTZ          NOT NULL DEFAULT now()
);

CREATE TABLE workspace (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120)     NOT NULL,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE member (
    workspace_id UUID        NOT NULL REFERENCES workspace (id),
    user_id      UUID        NOT NULL REFERENCES app_user (id),
    role         VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);

CREATE TABLE workspace_invite (
    token        VARCHAR(64) PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspace (id),
    created_by   UUID        NOT NULL REFERENCES app_user (id),
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account (
    id                           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id                 UUID         NOT NULL REFERENCES workspace (id),
    name                         VARCHAR(120) NOT NULL,
    type                         VARCHAR(12)  NOT NULL
        CHECK (type IN ('CHECKING', 'SAVINGS', 'CASH', 'INVESTMENT', 'OTHER')),
    initial_balance_cents        BIGINT       NOT NULL DEFAULT 0,
    default_contribution_pillar  VARCHAR(20)
        CHECK (default_contribution_pillar IN ('GOALS', 'FINANCIAL_FREEDOM')),
    color                        VARCHAR(7),
    icon                         VARCHAR(40),
    archived                     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ
);

CREATE INDEX ix_account_workspace ON account (workspace_id);

CREATE TABLE category (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID        NOT NULL REFERENCES workspace (id),
    parent_id    UUID        REFERENCES category (id),
    name         VARCHAR(80) NOT NULL,
    type         VARCHAR(8)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    pillar       VARCHAR(20)
        CHECK (pillar IN ('FIXED_COSTS', 'COMFORT', 'GOALS', 'PLEASURES', 'FINANCIAL_FREEDOM', 'KNOWLEDGE')),
    color        VARCHAR(7),
    icon         VARCHAR(40),
    system       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- EXPENSE requires a pillar, INCOME forbids it (PLANEJAMENTO §4)
    CONSTRAINT ck_category_pillar CHECK ((type = 'EXPENSE') = (pillar IS NOT NULL))
);

CREATE INDEX ix_category_workspace ON category (workspace_id);

CREATE TABLE pillar_allocation (
    workspace_id   UUID        NOT NULL REFERENCES workspace (id),
    pillar         VARCHAR(20) NOT NULL
        CHECK (pillar IN ('FIXED_COSTS', 'COMFORT', 'GOALS', 'PLEASURES', 'FINANCIAL_FREEDOM', 'KNOWLEDGE')),
    target_percent SMALLINT    NOT NULL CHECK (target_percent BETWEEN 0 AND 100),
    PRIMARY KEY (workspace_id, pillar)
);

CREATE TABLE transaction (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id         UUID         NOT NULL REFERENCES workspace (id),
    account_id           UUID         NOT NULL REFERENCES account (id),
    category_id          UUID         REFERENCES category (id),
    type                 VARCHAR(8)   NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount_cents         BIGINT       NOT NULL CHECK (amount_cents > 0),
    date                 DATE         NOT NULL,
    description          VARCHAR(255) NOT NULL,
    note                 TEXT,
    status               VARCHAR(8)   NOT NULL DEFAULT 'SETTLED' CHECK (status IN ('SETTLED', 'PENDING')),
    -- transfers are mirrored pairs (R4); each leg points at the other
    transfer_pair_id     UUID         REFERENCES transaction (id),
    -- set on transfer legs into INVESTMENT/SAVINGS accounts (R9)
    contribution_pillar  VARCHAR(20)
        CHECK (contribution_pillar IN ('GOALS', 'FINANCIAL_FREEDOM')),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ,
    CONSTRAINT ck_transaction_category CHECK ((type = 'TRANSFER') = (category_id IS NULL)),
    CONSTRAINT ck_transaction_pair CHECK (transfer_pair_id IS NULL OR type = 'TRANSFER')
);

CREATE INDEX ix_transaction_workspace_date ON transaction (workspace_id, date);
CREATE INDEX ix_transaction_account ON transaction (account_id);
CREATE INDEX ix_transaction_category ON transaction (category_id);

CREATE TABLE card (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id                UUID         NOT NULL REFERENCES workspace (id),
    name                        VARCHAR(120) NOT NULL,
    brand                       VARCHAR(20),
    limit_cents                 BIGINT       NOT NULL CHECK (limit_cents >= 0),
    closing_day                 SMALLINT     NOT NULL CHECK (closing_day BETWEEN 1 AND 28),
    due_day                     SMALLINT     NOT NULL CHECK (due_day BETWEEN 1 AND 28),
    default_payment_account_id  UUID         REFERENCES account (id),
    color                       VARCHAR(7),
    archived                    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ
);

CREATE INDEX ix_card_workspace ON card (workspace_id);

CREATE TABLE statement (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id         UUID        NOT NULL REFERENCES card (id),
    -- first day of the reference month
    reference_month DATE        NOT NULL,
    closing_date    DATE        NOT NULL,
    due_date        DATE        NOT NULL,
    status          VARCHAR(7)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'CLOSED', 'PAID', 'PARTIAL')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_statement_card_month UNIQUE (card_id, reference_month)
);

CREATE TABLE card_purchase (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id       UUID         NOT NULL REFERENCES workspace (id),
    card_id            UUID         NOT NULL REFERENCES card (id),
    category_id        UUID         NOT NULL REFERENCES category (id),
    description        VARCHAR(255) NOT NULL,
    total_amount_cents BIGINT       NOT NULL CHECK (total_amount_cents > 0),
    purchase_date      DATE         NOT NULL,
    installment_count  SMALLINT     NOT NULL DEFAULT 1 CHECK (installment_count >= 1),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ
);

CREATE INDEX ix_card_purchase_card ON card_purchase (card_id, purchase_date);

CREATE TABLE installment (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_id  UUID     NOT NULL REFERENCES card_purchase (id) ON DELETE CASCADE,
    number       SMALLINT NOT NULL CHECK (number >= 1),
    -- cent remainder of the split goes to the last installment (R1)
    amount_cents BIGINT   NOT NULL CHECK (amount_cents >= 0),
    statement_id UUID     NOT NULL REFERENCES statement (id),
    CONSTRAINT uq_installment_purchase_number UNIQUE (purchase_id, number)
);

CREATE INDEX ix_installment_statement ON installment (statement_id);

CREATE TABLE statement_payment (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id   UUID        NOT NULL REFERENCES statement (id),
    account_id     UUID        NOT NULL REFERENCES account (id),
    amount_cents   BIGINT      NOT NULL CHECK (amount_cents > 0),
    payment_date   DATE        NOT NULL,
    -- the EXPENSE leg debited on the account (system category, excluded from reports — R2)
    transaction_id UUID        REFERENCES transaction (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_statement_payment_statement ON statement_payment (statement_id);
