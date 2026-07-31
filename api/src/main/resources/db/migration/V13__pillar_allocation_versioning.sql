-- BE-6. Versions pillar_allocation by competency (PLANEJAMENTO §6.4/§6.6, R10): editing the six
-- targets must apply from the month of the edit onward, never rewriting how a closed month was
-- judged — a report for a past month keeps the percentages it was actually judged against.
--
-- V1 modeled exactly one row per (workspace, pillar), so an edit had nowhere to go but overwrite
-- history. This widens the primary key to (workspace_id, pillar, effective_from) so an edit inserts
-- a new row instead, and backfills the existing rows with a floor date far enough in the past that
-- they read as "in force since always" for every workspace that predates this migration — the same
-- floor date PillarAllocation.FLOOR_DATE now uses to seed a brand-new workspace.

ALTER TABLE pillar_allocation
    ADD COLUMN effective_from DATE;

UPDATE pillar_allocation
   SET effective_from = DATE '2000-01-01';

ALTER TABLE pillar_allocation
    ALTER COLUMN effective_from SET NOT NULL;

ALTER TABLE pillar_allocation
    DROP CONSTRAINT pillar_allocation_pkey;

ALTER TABLE pillar_allocation
    ADD CONSTRAINT pillar_allocation_pkey PRIMARY KEY (workspace_id, pillar, effective_from);

-- The read path (DashboardService.targets, PillarAllocationService.list) resolves "targets in force
-- at month X" per pillar as max(effective_from) <= first day of X — one lookup per workspace.
CREATE INDEX ix_pillar_allocation_workspace_effective
    ON pillar_allocation (workspace_id, pillar, effective_from DESC);
