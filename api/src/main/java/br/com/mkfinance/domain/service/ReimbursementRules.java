package br.com.mkfinance.domain.service;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;

/**
 * Pure rules of the reimbursement feature (ADR-0002): no Spring, no repositories. The service loads
 * the numbers; this class decides.
 */
public final class ReimbursementRules {

    private ReimbursementRules() {
    }

    /** Amount the user still expects to receive for an expense; never negative. */
    public static long pending(long reimbursableCents, long linkedCents) {
        return Math.max(0, reimbursableCents - linkedCents);
    }

    /** What an expense really cost the user, once the linked reimbursements are taken off. */
    public static long effectiveExpense(long amountCents, long linkedCents) {
        return amountCents - linkedCents;
    }

    /**
     * Validates linking {@code amountCents} of {@code income} to {@code expense}.
     *
     * @param incomeLinkedCents already linked from {@code income} to any expense
     * @param expenseLinkedCents already linked to {@code expense} from any income
     */
    public static void validateLink(
            Transaction income, Transaction expense, long amountCents, long incomeLinkedCents, long expenseLinkedCents) {
        if (income.getType() != TransactionType.INCOME
                || expense.getType() != TransactionType.EXPENSE
                || !income.getWorkspaceId().equals(expense.getWorkspaceId())
                || income.getStatus() != TransactionStatus.SETTLED
                || amountCents <= 0) {
            throw AppException.reimbursementInvalidLink();
        }
        if (incomeLinkedCents + amountCents > income.getAmountCents()) {
            throw AppException.reimbursementOverIncome(income.getAmountCents() - incomeLinkedCents);
        }
        long reimbursable = expense.getReimbursableCents() == null ? 0 : expense.getReimbursableCents();
        if (expenseLinkedCents + amountCents > reimbursable) {
            throw AppException.reimbursementOverExpense(Math.max(0, reimbursable - expenseLinkedCents));
        }
    }

    /** {@code reimbursableCents} is only valid on an expense, positive and not above its amount. */
    public static void validateReimbursable(TransactionType type, long amountCents, Long reimbursableCents) {
        if (reimbursableCents == null) {
            return;
        }
        if (type != TransactionType.EXPENSE || reimbursableCents <= 0 || reimbursableCents > amountCents) {
            throw AppException.reimbursableInvalid();
        }
    }
}
