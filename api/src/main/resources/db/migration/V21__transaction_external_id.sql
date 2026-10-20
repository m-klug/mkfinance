-- Idempotent bank-statement import (OFX): the bank's own id for a movement (OFX FITID) is stored on
-- the row it produced, so importing the same file twice finds the rows already there instead of
-- creating them again. FITID is only unique within one account (it is built from the posting date
-- and a per-day sequence), hence the index is per account. Nullable: every row not born from an
-- import keeps it empty, and the partial index leaves those out.

ALTER TABLE transaction
    ADD COLUMN external_id varchar(64);

CREATE UNIQUE INDEX uq_transaction_account_external_id
    ON transaction (account_id, external_id)
    WHERE external_id IS NOT NULL;
