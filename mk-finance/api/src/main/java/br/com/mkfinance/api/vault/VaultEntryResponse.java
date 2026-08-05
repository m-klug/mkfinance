package br.com.mkfinance.api.vault;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.VaultEntrySource;
import java.time.LocalDate;
import java.util.UUID;

/** Matches {@code components.schemas.VaultEntry} in contracts/openapi.yaml. */
public record VaultEntryResponse(
        UUID id,
        UUID vaultId,
        LocalDate date,
        String description,
        long amountCents,
        VaultEntrySource source,
        TransactionStatus status,
        UUID transactionId,
        UUID reallocationId) {
}
