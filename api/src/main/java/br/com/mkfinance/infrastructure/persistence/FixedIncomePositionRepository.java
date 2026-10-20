package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.FixedIncomePosition;
import br.com.mkfinance.domain.model.PositionStatus;
import br.com.mkfinance.domain.model.Transaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FixedIncomePositionRepository extends JpaRepository<FixedIncomePosition, UUID> {

    Optional<FixedIncomePosition> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Soonest maturity first: what is about to pay comes before what is far away. */
    List<FixedIncomePosition> findByWorkspaceIdOrderByMaturityDateAscCreatedAtAsc(UUID workspaceId);

    List<FixedIncomePosition> findByWorkspaceIdAndStatusOrderByMaturityDateAscCreatedAtAsc(
            UUID workspaceId, PositionStatus status);

    boolean existsByContributionTransactionId(UUID transactionId);

    /** A transaction is "in use" by a position as its purchase, its booked yield or its return transfer. */
    @Query("""
            select count(p) > 0 from FixedIncomePosition p
            where p.contributionTransactionId = :transactionId
               or p.yieldTransactionId = :transactionId
               or p.returnTransferTransactionId = :transactionId
            """)
    boolean existsReferencing(@Param("transactionId") UUID transactionId);

    /**
     * Purchases a position can still be born from: settled transfers into an investment or savings
     * account that no position claims yet, newest first. Only the IN leg is returned — it is where the
     * pillar and the vault live.
     */
    @Query("""
            select t from Transaction t, Account a
            where t.workspaceId = :workspaceId
              and t.type = br.com.mkfinance.domain.model.TransactionType.TRANSFER
              and t.transferDirection = br.com.mkfinance.domain.model.TransferDirection.IN
              and t.status = br.com.mkfinance.domain.model.TransactionStatus.SETTLED
              and a.id = t.accountId
              and a.type in (br.com.mkfinance.domain.model.AccountType.INVESTMENT,
                             br.com.mkfinance.domain.model.AccountType.SAVINGS)
              and not exists (select p.id from FixedIncomePosition p where p.contributionTransactionId = t.id)
            order by t.date desc, t.createdAt desc
            """)
    List<Transaction> findUnlinkedContributions(@Param("workspaceId") UUID workspaceId);
}
