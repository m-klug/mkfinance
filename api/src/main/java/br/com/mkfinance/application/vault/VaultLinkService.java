package br.com.mkfinance.application.vault;

import br.com.mkfinance.domain.model.VaultEntry;
import br.com.mkfinance.domain.model.VaultEntrySource;
import br.com.mkfinance.infrastructure.persistence.VaultEntryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where the vault ledger's mirror rows (CONTRIBUTION/WITHDRAWAL, §4.1) get written and removed —
 * DESENVOLVIMENTO-CAIXINHAS.md §4.4: "{@code TransferService} não ganha segunda responsabilidade."
 * BE-10's {@code TransferService} depends on this (a one-directional dependency,
 * {@code transaction → vault}, so there is no cycle) instead of writing {@code vault_entry} rows
 * itself.
 *
 * <p>Every method ends by asserting §4.3's invariant on whatever vault it just touched — the same
 * "works the same for any write path" discipline {@code VaultBalanceService.assertNotOverdrawn}
 * documents, so a caller here never needs its own bespoke "would this overdraw the vault" check.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VaultLinkService {

    private final VaultEntryRepository vaultEntryRepository;
    private final VaultBalanceService vaultBalanceService;

    /**
     * Creates the mirror ledger row for a transfer leg entering ({@code CONTRIBUTION}) or leaving
     * ({@code WITHDRAWAL}) a vault. {@code amountCents} is left {@code null} — §4.1's "mirror the
     * transaction" — so the caller must persist (and, if it is a new row, flush) the transaction
     * itself first: the ledger row's FK needs it to already exist.
     *
     * @param source CONTRIBUTION or WITHDRAWAL — REALLOCATION/YIELD never go through this service
     */
    public void link(UUID workspaceId, UUID vaultId, UUID transactionId, VaultEntrySource source) {
        VaultEntry entry = VaultEntry.builder()
                .workspaceId(workspaceId)
                .vaultId(vaultId)
                .source(source)
                .transactionId(transactionId)
                .build();
        vaultEntryRepository.saveAndFlush(entry);
        vaultBalanceService.assertNotOverdrawn(workspaceId, vaultId);
    }

    /**
     * Re-points a transfer leg's vault link — the {@code TransferService.update} case where
     * {@code accountId}/{@code vaultId} moved (§4.4: "omitido, carrega a caixinha atual adiante...").
     * Unlinks whatever this transaction currently mirrors, if anything, then links the new target,
     * if any; either half may be a no-op (no previous link, or no new vault — moving to a plain
     * account, or to an account with no vaults).
     */
    public void relink(UUID workspaceId, UUID transactionId, UUID newVaultId, VaultEntrySource newSource) {
        unlink(transactionId);
        if (newVaultId != null) {
            link(workspaceId, newVaultId, transactionId, newSource);
        }
    }

    /**
     * Removes the mirror ledger row for a transaction, if any. Callers deleting a transfer leg must
     * call this <strong>before</strong> deleting the transaction itself — deleting it after leaves a
     * stale {@code VaultEntry} in the same transaction's Hibernate persistence context even though
     * the row is gone from the database via {@code ON DELETE CASCADE} (armadilha 6,
     * DESENVOLVIMENTO-CAIXINHAS.md §7).
     */
    public void unlink(UUID transactionId) {
        vaultEntryRepository.findByTransactionIdAndAmountCentsIsNull(transactionId).ifPresent(entry -> {
            UUID workspaceId = entry.getWorkspaceId();
            UUID vaultId = entry.getVaultId();
            vaultEntryRepository.delete(entry);
            vaultEntryRepository.flush();
            vaultBalanceService.assertNotOverdrawn(workspaceId, vaultId);
        });
    }
}
