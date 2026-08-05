package br.com.mkfinance.api.vault;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Matches {@code components.schemas.VaultReallocationRequest} in contracts/openapi.yaml. */
public record VaultReallocationRequest(
        UUID fromVaultId,
        UUID toVaultId,
        @Min(1) long amountCents,
        @NotNull LocalDate date,
        @Size(max = 255) String description) {
}
