package br.com.mkfinance.application.vault;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Matches {@code components.schemas.VaultReallocation} in shape — {@code id} is the shared
 * {@code reallocationId} grouping the one or two {@code vault_entry} rows this reallocation wrote.
 */
public record VaultReallocationResult(
        UUID id, UUID accountId, UUID fromVaultId, UUID toVaultId, long amountCents, LocalDate date, String description) {
}
