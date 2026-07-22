package br.com.mkfinance.domain.service;

import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
import java.util.Collection;

/**
 * PLANEJAMENTO §5 R5 — the single place that turns transactions into an account balance.
 *
 * <pre>
 *   current   = initialBalance + Σ signed(SETTLED)
 *   projected = initialBalance + Σ signed(SETTLED ∪ PENDING)
 * </pre>
 *
 * <p>Pure and stateless (no Spring, no repositories) so the arithmetic is unit-testable on its own
 * and can be fed either row-by-row or from a pre-aggregated SQL {@code GROUP BY} — see
 * {@code application/transaction/AccountBalanceService}, which does the latter so listing accounts
 * costs one query regardless of history size.
 *
 * <p>Signs: INCOME adds, EXPENSE subtracts, and a transfer leg follows its
 * {@link TransferDirection} ({@code IN} adds, {@code OUT} subtracts). Because a transfer's two
 * legs carry opposite signs on different accounts, transfers move money without ever creating
 * income or expense (R4) — the total across the workspace is unchanged.
 */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    /**
     * One transaction, or one pre-aggregated {@code GROUP BY (type, status, direction)} bucket
     * whose {@code amountCents} is the bucket's SUM. Both feed the same arithmetic.
     */
    public record Entry(
            TransactionType type, TransactionStatus status, TransferDirection direction, long amountCents) {

        public static Entry of(Transaction transaction) {
            return new Entry(
                    transaction.getType(),
                    transaction.getStatus(),
                    transaction.getTransferDirection(),
                    transaction.getAmountCents());
        }
    }

    /** Current (settled) and projected (settled + pending) balance of an account, in cents. */
    public record Balance(long currentCents, long projectedCents) {

        public static Balance ofInitial(long initialBalanceCents) {
            return new Balance(initialBalanceCents, initialBalanceCents);
        }
    }

    /**
     * How much this entry moves its account's balance: positive for money in, negative for money
     * out. A TRANSFER leg without a direction is a data-integrity bug (the DB forbids it), not
     * something to silently treat as zero.
     */
    public static long signedAmountCents(Entry entry) {
        return switch (entry.type()) {
            case INCOME -> entry.amountCents();
            case EXPENSE -> -entry.amountCents();
            case TRANSFER -> {
                if (entry.direction() == null) {
                    throw new IllegalStateException("Transfer leg without a transferDirection");
                }
                yield entry.direction() == TransferDirection.IN ? entry.amountCents() : -entry.amountCents();
            }
        };
    }

    /** Initial balance + settled movements (R5). */
    public static long currentBalanceCents(long initialBalanceCents, Collection<Entry> entries) {
        long balance = initialBalanceCents;
        for (Entry entry : entries) {
            if (entry.status() == TransactionStatus.SETTLED) {
                balance += signedAmountCents(entry);
            }
        }
        return balance;
    }

    /** Initial balance + settled and pending movements — the "saldo projetado" (R5). */
    public static long projectedBalanceCents(long initialBalanceCents, Collection<Entry> entries) {
        long balance = initialBalanceCents;
        for (Entry entry : entries) {
            balance += signedAmountCents(entry);
        }
        return balance;
    }

    /** Both balances in a single pass — what the accounts endpoint needs per account. */
    public static Balance balance(long initialBalanceCents, Collection<Entry> entries) {
        long current = initialBalanceCents;
        long projected = initialBalanceCents;
        for (Entry entry : entries) {
            long signed = signedAmountCents(entry);
            projected += signed;
            if (entry.status() == TransactionStatus.SETTLED) {
                current += signed;
            }
        }
        return new Balance(current, projected);
    }
}
