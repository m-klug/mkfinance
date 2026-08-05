-- BE-8 (Fase 2) — the many-to-many between `transaction` and `tag`, scoped only to `transaction`
-- (card_purchase/statement/installment carry no workspace_id of their own, so tagging them is a
-- bigger change left out of this phase). No JPA relationship maps this table (PLANEJAMENTO
-- convention): both sides are plain UUID columns, joined explicitly wherever tags are read.

CREATE TABLE transaction_tag (
    transaction_id UUID NOT NULL REFERENCES transaction (id) ON DELETE CASCADE,
    tag_id         UUID NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (transaction_id, tag_id)
);

-- Deleting a tag (DELETE /tags/{tagId}) unlinks it from every transaction via the FK cascade above;
-- this index is what makes that fast, and what the "which transactions carry tag X" side of the OR
-- filter reads.
CREATE INDEX ix_transaction_tag_tag ON transaction_tag (tag_id);
