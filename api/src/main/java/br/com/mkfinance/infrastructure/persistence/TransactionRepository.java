package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Transaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The extrato's optional filters (period, account, category, type, status, text) are expressed as
 * Criteria {@code Specification}s — see {@code application/transaction/TransactionSpecifications} —
 * rather than a JPQL query full of {@code :param is null or ...}, so a skipped filter emits no SQL
 * at all instead of relying on the driver inferring the type of a null bind parameter.
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Both legs of a mirrored transfer pair (R4) — used to keep edits/deletes atomic. */
    List<Transaction> findByTransferPairId(UUID transferPairId);

    /**
     * Pre-aggregated input for {@code BalanceCalculator}: one row per
     * {@code (account, type, status, direction)} bucket in the workspace, so every account's
     * balance (R5) costs a single query regardless of how long the history is.
     */
    @Query("""
            select new br.com.mkfinance.infrastructure.persistence.TransactionBalanceBucket(
                       t.accountId, t.type, t.status, t.transferDirection, sum(t.amountCents))
            from Transaction t
            where t.workspaceId = :workspaceId
            group by t.accountId, t.type, t.status, t.transferDirection
            """)
    List<TransactionBalanceBucket> aggregateBalanceBuckets(@Param("workspaceId") UUID workspaceId);
}
