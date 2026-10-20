-- Idempotent card-statement import (PDF): the Inter PDF carries no id per line, so the importer
-- derives a fingerprint (card, date, amount, the bank's own description, installment, occurrence
-- within the file) and stores it on the installment the line became or was linked to. Importing the
-- same bill twice then finds the lines already there. A purchase typed by hand starts with no
-- external_id and gets one when a line of the bill is linked to it, which is how a hand-made entry
-- and its later bill line stay one record. Scoped per statement: a line belongs to exactly one bill.
-- Nullable: every installment not born from or linked to an import keeps it empty, and the partial
-- index leaves those out.

ALTER TABLE installment
    ADD COLUMN external_id varchar(64);

CREATE UNIQUE INDEX uq_installment_statement_external_id
    ON installment (statement_id, external_id)
    WHERE external_id IS NOT NULL;
