package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransferDirection;
import java.util.UUID;

/**
 * One {@code group by (vault, status, direction)} row of
 * {@link VaultEntryRepository#aggregateMirrorBuckets(UUID)} — bucket A of
 * DESENVOLVIMENTO-CAIXINHAS.md §5.5's table (CONTRIBUTION/WITHDRAWAL). {@code amountCents} is
 * always positive here, exactly as stored on {@code transaction}; the type is always TRANSFER
 * (only a transfer leg ever creates one of these two sources), so the sign is resolved by
 * {@code domain.service.VaultBalanceCalculator.Entry#mirror}, which reuses
 * {@code BalanceCalculator#signedAmountCents} literally.
 */
public record VaultMirrorBucket(UUID vaultId, TransactionStatus status, TransferDirection direction, Long amountCents) {
}
