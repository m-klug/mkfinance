package br.com.mkfinance.application.vault;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.VaultEntrySource;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One resolved row of a vault's ledger (contract {@code listVaultEntries}) — a {@link
 * br.com.mkfinance.domain.model.VaultEntry} with its date/description/amount already resolved
 * against the transaction behind it, since CONTRIBUTION/WITHDRAWAL/YIELD carry none of those on
 * the ledger table itself (only REALLOCATION does).
 *
 * @param amountCents signed — negative when money left the vault (the one exception in the API,
 *     documented on the contract's {@code VaultEntry.amountCents})
 * @param createdAt only used to break ties when two rows share the same {@link #date}
 */
public record VaultLedgerLine(
        UUID id,
        UUID vaultId,
        LocalDate date,
        String description,
        long amountCents,
        VaultEntrySource source,
        TransactionStatus status,
        UUID transactionId,
        UUID reallocationId,
        OffsetDateTime createdAt) {
}
