package br.com.mkfinance.infrastructure.persistence;

import java.util.UUID;

/**
 * One {@code group by (vault)} row of
 * {@link VaultEntryRepository#aggregateReallocationBuckets(UUID)} — bucket C of
 * DESENVOLVIMENTO-CAIXINHAS.md §5.5's table. No status column: a reallocation always counts as
 * settled (§4.2), so {@code VaultBalanceService} builds the calculator's {@code Entry} with
 * {@code TransactionStatus.SETTLED} itself rather than carrying one here.
 */
public record VaultReallocationBucket(UUID vaultId, Long amountCents) {
}
