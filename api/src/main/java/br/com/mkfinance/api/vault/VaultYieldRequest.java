package br.com.mkfinance.api.vault;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Matches {@code components.schemas.VaultYieldRequest} in contracts/openapi.yaml. */
public record VaultYieldRequest(@Min(1) long amountCents, @NotNull LocalDate date, @Size(max = 255) String description) {
}
