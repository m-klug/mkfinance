package br.com.mkfinance.application.reimbursement;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Reimbursement;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.service.ReimbursementRules;
import br.com.mkfinance.infrastructure.persistence.ReimbursementRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links an INCOME to the EXPENSE it paid back (ADR-0002). Every read and write is scoped by
 * {@code workspaceId}; the numeric rules live in {@link ReimbursementRules}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReimbursementService {

    private final ReimbursementRepository reimbursementRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Links {@code amountCents} of the income to the expense. Linking the same pair again adds to
     * the existing link instead of failing, so "I received the rest" is one more click.
     */
    public Reimbursement link(UUID workspaceId, UUID incomeId, UUID expenseId, long amountCents) {
        Transaction income = transactionRepository
                .findByIdAndWorkspaceId(incomeId, workspaceId)
                .orElseThrow(AppException::transactionNotFound);
        Transaction expense = transactionRepository
                .findByIdAndWorkspaceId(expenseId, workspaceId)
                .orElseThrow(AppException::transactionNotFound);

        ReimbursementRules.validateLink(
                income,
                expense,
                amountCents,
                reimbursementRepository.sumByIncome(incomeId),
                reimbursementRepository.sumByExpense(expenseId));

        Reimbursement existing = reimbursementRepository
                .findByIncomeTransactionIdAndExpenseTransactionId(incomeId, expenseId)
                .orElse(null);
        if (existing != null) {
            existing.setAmountCents(existing.getAmountCents() + amountCents);
            return reimbursementRepository.save(existing);
        }
        return reimbursementRepository.save(Reimbursement.builder()
                .workspaceId(workspaceId)
                .incomeTransactionId(incomeId)
                .expenseTransactionId(expenseId)
                .amountCents(amountCents)
                .build());
    }

    @Transactional(readOnly = true)
    public List<Reimbursement> listFor(UUID workspaceId, UUID transactionId) {
        transactionRepository
                .findByIdAndWorkspaceId(transactionId, workspaceId)
                .orElseThrow(AppException::transactionNotFound);
        return reimbursementRepository.findByTransaction(workspaceId, transactionId);
    }

    public void unlink(UUID workspaceId, UUID reimbursementId) {
        Reimbursement reimbursement = reimbursementRepository
                .findByIdAndWorkspaceId(reimbursementId, workspaceId)
                .orElseThrow(AppException::reimbursementNotFound);
        reimbursementRepository.delete(reimbursement);
    }

    /** Expenses that still wait for money, oldest first, any month. */
    @Transactional(readOnly = true)
    public List<Transaction> pendingExpenses(UUID workspaceId) {
        return transactionRepository.findReimbursablePending(workspaceId);
    }

    /** What is linked to this transaction, whichever side it is on. */
    @Transactional(readOnly = true)
    public long linkedTotal(UUID transactionId) {
        return reimbursementRepository.sumByExpense(transactionId) + reimbursementRepository.sumByIncome(transactionId);
    }

    /** Linked totals for many transactions in two queries. Ids with nothing linked are absent. */
    @Transactional(readOnly = true)
    public Map<UUID, Long> linkedTotals(Collection<UUID> transactionIds) {
        Map<UUID, Long> totals = new HashMap<>();
        if (transactionIds.isEmpty()) {
            return totals;
        }
        for (Object[] row : reimbursementRepository.sumsByExpenses(transactionIds)) {
            totals.merge((UUID) row[0], (Long) row[1], Long::sum);
        }
        for (Object[] row : reimbursementRepository.sumsByIncomes(transactionIds)) {
            totals.merge((UUID) row[0], (Long) row[1], Long::sum);
        }
        return totals;
    }
}
