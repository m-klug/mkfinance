package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Reimbursement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReimbursementRepository extends JpaRepository<Reimbursement, UUID> {

    Optional<Reimbursement> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Reimbursement> findByIncomeTransactionIdAndExpenseTransactionId(UUID incomeId, UUID expenseId);

    /** Links touching this transaction on either side, oldest first. */
    @Query("""
            select r from Reimbursement r
            where r.workspaceId = :workspaceId
              and (r.incomeTransactionId = :transactionId or r.expenseTransactionId = :transactionId)
            order by r.createdAt asc, r.id asc
            """)
    List<Reimbursement> findByTransaction(
            @Param("workspaceId") UUID workspaceId, @Param("transactionId") UUID transactionId);

    @Query("select coalesce(sum(r.amountCents), 0) from Reimbursement r where r.incomeTransactionId = :incomeId")
    long sumByIncome(@Param("incomeId") UUID incomeId);

    @Query("select coalesce(sum(r.amountCents), 0) from Reimbursement r where r.expenseTransactionId = :expenseId")
    long sumByExpense(@Param("expenseId") UUID expenseId);

    /** {@code [expenseId, sum]} rows — one query for a whole extrato page. */
    @Query("""
            select r.expenseTransactionId, sum(r.amountCents) from Reimbursement r
            where r.expenseTransactionId in :ids group by r.expenseTransactionId
            """)
    List<Object[]> sumsByExpenses(@Param("ids") Collection<UUID> ids);

    /** {@code [incomeId, sum]} rows — one query for a whole extrato page. */
    @Query("""
            select r.incomeTransactionId, sum(r.amountCents) from Reimbursement r
            where r.incomeTransactionId in :ids group by r.incomeTransactionId
            """)
    List<Object[]> sumsByIncomes(@Param("ids") Collection<UUID> ids);
}
