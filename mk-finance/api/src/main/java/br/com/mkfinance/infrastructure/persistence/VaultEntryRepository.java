package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.VaultEntry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The vault ledger (V19__vault_entry.sql). No JPA relationship backs {@code vault_id} /
 * {@code transaction_id} (PLANEJAMENTO convention) — every cross-entity read here joins the plain
 * UUID columns explicitly, the same pattern {@code InstallmentRepository} uses for
 * {@code purchase_id}/{@code statement_id}.
 *
 * <p>The three aggregations mirror DESENVOLVIMENTO-CAIXINHAS.md §5.5's table exactly, each a
 * {@code group by} costing a single query over the whole workspace — never a sum computed in Java
 * over a loaded list, the same discipline {@code TransactionRepository#aggregateBalanceBuckets}
 * follows for accounts.
 */
public interface VaultEntryRepository extends JpaRepository<VaultEntry, UUID> {

    /** DELETE guard (VAULT_HAS_ENTRIES) — a vault with any ledger history cannot be deleted, only archived. */
    boolean existsByVaultId(UUID vaultId);

    /**
     * Bucket A: CONTRIBUTION/WITHDRAWAL, mirroring a transfer leg. {@code amount_cents} is NULL on
     * these rows by construction (V19's check constraint), so the value always comes from the
     * paired transaction — never summed from the ledger's own (null) column.
     */
    @Query("""
            select new br.com.mkfinance.infrastructure.persistence.VaultMirrorBucket(
                       ve.vaultId, t.status, t.transferDirection, sum(t.amountCents))
            from VaultEntry ve, Transaction t
            where ve.transactionId = t.id
              and ve.workspaceId = :workspaceId
              and ve.source in (
                  br.com.mkfinance.domain.model.VaultEntrySource.CONTRIBUTION,
                  br.com.mkfinance.domain.model.VaultEntrySource.WITHDRAWAL)
            group by ve.vaultId, t.status, t.transferDirection
            """)
    List<VaultMirrorBucket> aggregateMirrorBuckets(@Param("workspaceId") UUID workspaceId);

    /** Bucket B: one vault's share of a distributed yield, already signed on the ledger itself. */
    @Query("""
            select new br.com.mkfinance.infrastructure.persistence.VaultYieldBucket(
                       ve.vaultId, t.status, sum(ve.amountCents))
            from VaultEntry ve, Transaction t
            where ve.transactionId = t.id
              and ve.workspaceId = :workspaceId
              and ve.source = br.com.mkfinance.domain.model.VaultEntrySource.YIELD
            group by ve.vaultId, t.status
            """)
    List<VaultYieldBucket> aggregateYieldBuckets(@Param("workspaceId") UUID workspaceId);

    /** Bucket C: reallocation legs — no transaction, no status column (always settled, §4.2). */
    @Query("""
            select new br.com.mkfinance.infrastructure.persistence.VaultReallocationBucket(
                       ve.vaultId, sum(ve.amountCents))
            from VaultEntry ve
            where ve.workspaceId = :workspaceId
              and ve.source = br.com.mkfinance.domain.model.VaultEntrySource.REALLOCATION
            group by ve.vaultId
            """)
    List<VaultReallocationBucket> aggregateReallocationBuckets(@Param("workspaceId") UUID workspaceId);

    /** The mirrored ledger row of a transfer leg, addressed by its transaction — used by link/unlink. */
    Optional<VaultEntry> findByTransactionIdAndAmountCentsIsNull(UUID transactionId);

    /**
     * Batch version of {@link #findByTransactionIdAndAmountCentsIsNull} — the extrato's {@code
     * Transaction.vaultId} badge (BE-10), one query for a whole page instead of one per row, in the
     * same spirit as {@code TagService.tagsOf(Collection)}. {@code amount_cents IS NULL} is what
     * scopes this to CONTRIBUTION/WITHDRAWAL mirrors (V19's check constraint); a YIELD transaction
     * never resolves here since it cannot collapse to a single vault (1 transaction : N vaults).
     */
    List<VaultEntry> findByTransactionIdInAndAmountCentsIsNull(Collection<UUID> transactionIds);

    /** Every ledger row mirroring this transaction, mirror or yield share alike — used by delete cascade helpers. */
    List<VaultEntry> findByTransactionId(UUID transactionId);

    /**
     * Both legs of a reallocation group — DELETE undoes them together (contract). Scoped by
     * workspace (R8): {@code reallocationId} is an opaque grouping value, not a lookup key another
     * workspace's rows could never share by accident, but every by-id read still filters explicitly.
     */
    List<VaultEntry> findByReallocationIdAndWorkspaceId(UUID reallocationId, UUID workspaceId);

    /**
     * Every row of a vault's own ledger (contract {@code listVaultEntries}), unsorted and
     * unfiltered — {@code application/vault/VaultEntryService} resolves each row's date/amount
     * against its transaction (batched, one query) and sorts/filters in memory, since
     * CONTRIBUTION/WITHDRAWAL/YIELD carry no date of their own on this table (they mirror the
     * transaction behind {@link VaultEntry#getTransactionId()}) while REALLOCATION does. A vault's
     * history is bounded by what a person can contribute to one goal, not by workspace-wide volume,
     * so this is not the kind of aggregate {@code AccountBalanceService}/{@code VaultBalanceService}
     * must keep at the database.
     */
    List<VaultEntry> findByVaultId(UUID vaultId);
}
