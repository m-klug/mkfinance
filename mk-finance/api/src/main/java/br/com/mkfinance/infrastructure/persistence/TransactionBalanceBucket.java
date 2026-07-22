package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
import java.util.UUID;

/**
 * One {@code group by (account, type, status, direction)} row of
 * {@link TransactionRepository#aggregateBalanceBuckets(UUID)}, where {@code amountCents} is the
 * bucket's SUM. Feeding {@code BalanceCalculator} these instead of raw rows keeps balance
 * computation at one query and a handful of rows per workspace.
 */
public record TransactionBalanceBucket(
        UUID accountId,
        TransactionType type,
        TransactionStatus status,
        TransferDirection direction,
        Long amountCents) {
}
