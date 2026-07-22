-- BE-3. V1 models a transfer (R4) as a mirrored pair of rows sharing `transfer_pair_id`, but both
-- rows must store a positive `amount_cents` (ck on the column), so nothing in the schema says which
-- leg lost the money and which one received it. Without that, a balance (R5) cannot be computed
-- from a single row and an allocation report (R9/R10) cannot tell a contribution's destination.
--
-- This adds the missing discriminator plus the invariant that keeps contributions single-counted:
-- only the incoming leg may carry `contribution_pillar`, so summing that column per pillar can
-- never count one transfer twice.

ALTER TABLE transaction
    ADD COLUMN transfer_direction VARCHAR(3)
        CHECK (transfer_direction IN ('OUT', 'IN'));

-- A transfer leg always has a direction; an INCOME/EXPENSE row never does.
ALTER TABLE transaction
    ADD CONSTRAINT ck_transaction_direction
        CHECK ((type = 'TRANSFER') = (transfer_direction IS NOT NULL));

-- A contribution pillar only ever lives on the leg arriving in the INVESTMENT/SAVINGS account.
ALTER TABLE transaction
    ADD CONSTRAINT ck_transaction_contribution_leg
        CHECK (contribution_pillar IS NULL OR transfer_direction = 'IN');

-- Loading the sibling leg happens on every transfer edit/settle/delete.
CREATE INDEX ix_transaction_transfer_pair ON transaction (transfer_pair_id);

-- Balance aggregation groups by (account, type, status, direction) within a workspace.
CREATE INDEX ix_transaction_workspace_account ON transaction (workspace_id, account_id);
