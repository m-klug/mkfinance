package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.TransactionStatus;
import java.util.UUID;

/**
 * One {@code group by (vault, status)} row of
 * {@link VaultEntryRepository#aggregateYieldBuckets(UUID)} — bucket B of
 * DESENVOLVIMENTO-CAIXINHAS.md §5.5's table. {@code amountCents} is already signed on the ledger
 * itself (a yield share is always positive); the status comes from the yield's INCOME transaction.
 */
public record VaultYieldBucket(UUID vaultId, TransactionStatus status, Long amountCents) {
}
