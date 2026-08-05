-- BE-8 (Fase 2) — free-text tags, multiple per transaction. A tag is just its name (CT-1): typing
-- one that does not exist yet on TransactionRequest.tags creates it, no separate round-trip needed.

CREATE TABLE tag (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID        NOT NULL REFERENCES workspace (id),
    name         VARCHAR(40) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_tag_workspace ON tag (workspace_id);

-- Case-insensitive uniqueness per workspace: "Trabalho" and "trabalho" are the same tag, so creating
-- one that only differs by case answers 409 TAG_NAME_DUPLICATE instead of a silent duplicate.
CREATE UNIQUE INDEX ux_tag_workspace_name_ci ON tag (workspace_id, lower(name));
