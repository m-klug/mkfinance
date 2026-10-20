-- Historico diario do CDI e da Selic, usado para calcular quanto um titulo pos-fixado ja rendeu ate
-- hoje: o rendimento passado depende do que o indice realmente foi em cada dia util, nao do valor
-- atual. Dado publico, igual para todos os espacos (sem workspace_id), vindo da API SGS do Banco
-- Central.
--   CDI   : SGS 12, taxa do dia em % ao dia
--   SELIC : SGS 11, taxa Selic efetiva do dia em % ao dia (e a que o Tesouro Selic rende, nao a meta)
-- Uma linha por (indice, dia util). Dias sem linha sao dias nao uteis ou ainda nao publicados.

CREATE TABLE index_daily (
    id             uuid           PRIMARY KEY,
    code           varchar(8)     NOT NULL CHECK (code IN ('CDI', 'SELIC')),
    reference_date date           NOT NULL,
    daily_percent  numeric(14, 8) NOT NULL CHECK (daily_percent >= 0),
    fetched_at     timestamptz    NOT NULL,
    CONSTRAINT uq_index_daily_code_date UNIQUE (code, reference_date)
);
