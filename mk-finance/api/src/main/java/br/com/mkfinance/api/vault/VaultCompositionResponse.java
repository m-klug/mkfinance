package br.com.mkfinance.api.vault;

import java.util.List;
import java.util.UUID;

/**
 * Matches {@code components.schemas.VaultComposition} in contracts/openapi.yaml — the whole
 * account composition in one response (CT-2 decision 1). The client never sums money to find the
 * unallocated slice; {@code unallocatedCents}/{@code projectedUnallocatedCents} arrive computed and
 * already checked against the Σ vaults + unallocated = account balance identity (§4.3).
 */
public record VaultCompositionResponse(
        UUID accountId,
        long balanceCents,
        long projectedBalanceCents,
        long allocatedCents,
        long unallocatedCents,
        long projectedUnallocatedCents,
        List<VaultResponse> vaults) {
}
