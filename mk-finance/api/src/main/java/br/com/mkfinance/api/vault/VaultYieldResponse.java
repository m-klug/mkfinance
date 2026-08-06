package br.com.mkfinance.api.vault;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Matches {@code components.schemas.VaultYield} in contracts/openapi.yaml. */
public record VaultYieldResponse(UUID transactionId, long amountCents, LocalDate date, List<VaultYieldShareResponse> shares) {
}
