CREATE TABLE app_user (
    id         UUID PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE asset (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES app_user (id),
    ticker     VARCHAR(12)  NOT NULL,
    name       VARCHAR(120) NOT NULL,
    market     VARCHAR(2)   NOT NULL CHECK (market IN ('BR', 'US')),
    category   VARCHAR(10)  NOT NULL CHECK (category IN ('STOCK_BR', 'FII', 'ETF_BR', 'UNIT', 'STOCK_US', 'REIT_US', 'ETF_US')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_asset_user_ticker UNIQUE (user_id, ticker)
);

CREATE TABLE investment_order (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seq         BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id     UUID           NOT NULL REFERENCES app_user (id),
    asset_id    UUID           NOT NULL REFERENCES asset (id),
    order_type  VARCHAR(13)    NOT NULL CHECK (order_type IN ('BUY', 'SELL', 'SPLIT', 'REVERSE_SPLIT', 'AMORTIZATION', 'BONUS_SHARES')),
    trade_date  DATE           NOT NULL,
    quantity    NUMERIC(18, 6) NOT NULL CHECK (quantity > 0),
    order_value NUMERIC(18, 6) NOT NULL CHECK (order_value >= 0),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ
);

CREATE INDEX ix_order_asset_date_seq ON investment_order (asset_id, trade_date, seq);

CREATE TABLE ptax_quote (
    quote_date    DATE PRIMARY KEY,
    buy_rate      NUMERIC(10, 4) NOT NULL,
    sell_rate     NUMERIC(10, 4) NOT NULL,
    bcb_timestamp TIMESTAMPTZ,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
