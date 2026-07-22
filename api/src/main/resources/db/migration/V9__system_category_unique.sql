-- BE-4 (credit card).
--
-- The system "statement payment" category (R2) is created lazily, the first time a workspace pays a
-- statement, rather than seeded at registration — that way workspaces created before the credit-card
-- feature shipped get it too. Lazy creation is a find-or-create, so it needs the database to arbitrate
-- when two payments race: without this index both would insert and the workspace would end up with
-- two "Pagamento de fatura" categories, silently splitting the rows reports have to exclude.
--
-- Partial (WHERE system) so it constrains only system categories: users remain free to name their own
-- categories however they like, duplicates included.
CREATE UNIQUE INDEX uq_category_system_name
    ON category (workspace_id, name)
    WHERE system;
