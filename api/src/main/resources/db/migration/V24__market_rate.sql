-- Taxas de mercado (CDI, Selic, IPCA) usadas pelo simulador de renda fixa. Dado publico, igual para
-- todos os espacos: por isso nao tem workspace_id. Vem da API SGS do Banco Central e e guardado para
-- o simulador continuar funcionando quando o BC estiver fora do ar. Uma linha por (taxa, data de
-- referencia); a vigente e a de data mais recente.
--   CDI   : % a.a. (SGS 4389, CDI anualizada base 252)
--   SELIC : % a.a. (SGS 432, meta definida pelo Copom)
--   IPCA  : % acumulado em 12 meses (SGS 13522), usado so como sugestao de premissa

CREATE TABLE market_rate (
    id             uuid          PRIMARY KEY,
    code           varchar(8)    NOT NULL CHECK (code IN ('CDI', 'SELIC', 'IPCA')),
    reference_date date          NOT NULL,
    rate_percent   numeric(9, 4) NOT NULL,
    fetched_at     timestamptz   NOT NULL,
    CONSTRAINT uq_market_rate_code_date UNIQUE (code, reference_date)
);
