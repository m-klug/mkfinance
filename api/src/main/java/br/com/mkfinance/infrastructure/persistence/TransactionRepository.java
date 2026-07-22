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
     * Whether this transaction is the debit side of a credit-card statement payment (R2), in which
     * case the extrato must refuse to delete it and send the user to reverse it on the statement.
     *
     * <p>{@code statement_payment} belongs to BE-4, so this is a bare existence check written as
     * native SQL — the same cross-feature guard pattern as
     * {@code CategoryRepository#existsCardPurchaseForCategory}. Asking here, rather than letting the
     * foreign key raise, is what turns a generic DATA_INTEGRITY_VIOLATION into an error the client
     * can act on.
     */
    @Query(
            value = "select exists(select 1 from statement_payment where transaction_id = :transactionId)",
            nativeQuery = true)
    boolean existsStatementPaymentForTransaction(@Param("transactionId") UUID transactionId);

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
