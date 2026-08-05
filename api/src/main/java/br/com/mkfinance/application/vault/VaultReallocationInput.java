package br.com.mkfinance.application.vault;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The application-layer shape of {@code VaultReallocationRequest} (CT-2). Either
 * {@code fromVaultId}/{@code toVaultId} may be null, meaning the unallocated slice (§4.7) — both
 * null at once is rejected by {@code VaultReallocationService}, not by this record.
 */
public record VaultReallocationInput(
        UUID fromVaultId, UUID toVaultId, long amountCents, LocalDate date, String description) {
}
