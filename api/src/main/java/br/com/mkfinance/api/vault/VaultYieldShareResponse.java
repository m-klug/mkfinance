package br.com.mkfinance.api.vault;

import java.util.UUID;

/** Matches one item of {@code components.schemas.VaultYield.shares} in contracts/openapi.yaml. */
public record VaultYieldShareResponse(UUID vaultId, long amountCents) {
}
