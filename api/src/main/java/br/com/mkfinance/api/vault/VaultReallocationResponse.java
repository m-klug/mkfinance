package br.com.mkfinance.api.vault;

import java.time.LocalDate;
import java.util.UUID;

/** Matches {@code components.schemas.VaultReallocation} in contracts/openapi.yaml. */
public record VaultReallocationResponse(
        UUID id, UUID accountId, UUID fromVaultId, UUID toVaultId, long amountCents, LocalDate date, String description) {
}
