-- Admits KNOWLEDGE as a third contribution pillar (R9). Study money is saved up before it is
-- spent — set aside month after month, spent on a course later — which is exactly the shape GOALS
-- and FINANCIAL_FREEDOM already had, and the reason those two needed an aporte to consume their
-- percentage at all. The other three pillars (FIXED_COSTS, COMFORT, PLEASURES) stay out: they are
-- consumed as they are spent, so parking money for them would count the same money twice.
--
-- KNOWLEDGE is not new here — it has been one of the six `pillar` values since V1 (V2 seeds no
-- category for it, but a user-created one is free to use it). All this widens is the *contribution*
-- subset, in the three places the schema pins it down. Nothing migrates: every existing row keeps
-- the pillar it already had, and the three constraints only ever get looser, so this cannot fail on
-- production data.

-- Column CHECKs written inline in V1/V18 carry Postgres' default name, `<table>_<column>_check`.

ALTER TABLE account
    DROP CONSTRAINT account_default_contribution_pillar_check;
ALTER TABLE account
    ADD CONSTRAINT account_default_contribution_pillar_check
        CHECK (default_contribution_pillar IN ('GOALS', 'FINANCIAL_FREEDOM', 'KNOWLEDGE'));

ALTER TABLE transaction
    DROP CONSTRAINT transaction_contribution_pillar_check;
ALTER TABLE transaction
    ADD CONSTRAINT transaction_contribution_pillar_check
        CHECK (contribution_pillar IN ('GOALS', 'FINANCIAL_FREEDOM', 'KNOWLEDGE'));

ALTER TABLE vault
    DROP CONSTRAINT vault_contribution_pillar_check;
ALTER TABLE vault
    ADD CONSTRAINT vault_contribution_pillar_check
        CHECK (contribution_pillar IN ('GOALS', 'FINANCIAL_FREEDOM', 'KNOWLEDGE'));
