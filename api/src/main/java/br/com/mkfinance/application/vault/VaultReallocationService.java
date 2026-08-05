package br.com.mkfinance.application.vault;

import br.com.mkfinance.application.vault.VaultValidationException.FieldViolation;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Vault;
import br.com.mkfinance.domain.model.VaultEntry;
import br.com.mkfinance.domain.model.VaultEntrySource;
import br.com.mkfinance.infrastructure.persistence.VaultEntryRepository;
import br.com.mkfinance.infrastructure.persistence.VaultRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remanejo — money moved between vaults of the same account, or to/from the unallocated slice
 * (DESENVOLVIMENTO-CAIXINHAS.md §4.7). No account balance changes and no pillar is consumed: the
 * money already consumed its pillar (or picked none) when it first arrived. Writes zero, one, or
 * two {@link VaultEntry} rows sharing a single {@code reallocationId} — DELETE always undoes the
 * whole group at once.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VaultReallocationService {

    private final VaultRepository vaultRepository;
    private final VaultEntryRepository vaultEntryRepository;
    private final VaultBalanceService vaultBalanceService;

    /**
     * §4.7's table: either side may be null (meaning the unallocated slice), but not both.
     * {@code fromVaultId == toVaultId} (both naming the same vault) is not special-cased — the two
     * opposite-signed entries simply cancel out, which is harmless and does not need its own rule.
     *
     * @throws AppException VAULT_NOT_FOUND (unknown/other-workspace id), VAULT_ARCHIVED (archiving
     *     is only for empty vaults, so a reallocation into/out of one is always a contradiction),
     *     VAULT_ACCOUNT_MISMATCH (both sides named, different accounts — money cannot leave the
     *     account through a reallocation), VAULT_INSUFFICIENT_BALANCE (the source would go negative)
     */
    public VaultReallocationResult create(UUID workspaceId, VaultReallocationInput input) {
        if (input.fromVaultId() == null && input.toVaultId() == null) {
            throw new VaultValidationException(List.of(
                    new FieldViolation("fromVaultId", "REQUIRED_UNLESS_TO_VAULT_ID_IS_SET"),
                    new FieldViolation("toVaultId", "REQUIRED_UNLESS_FROM_VAULT_ID_IS_SET")));
        }

        Vault fromVault = resolveActiveVault(workspaceId, input.fromVaultId());
        Vault toVault = resolveActiveVault(workspaceId, input.toVaultId());
        if (fromVault != null && toVault != null && !fromVault.getAccountId().equals(toVault.getAccountId())) {
            throw AppException.vaultAccountMismatch();
        }
        UUID accountId = fromVault != null ? fromVault.getAccountId() : toVault.getAccountId();

        UUID reallocationId = UUID.randomUUID();
        List<VaultEntry> entries = new ArrayList<>(2);
        if (fromVault != null) {
            entries.add(entry(workspaceId, fromVault.getId(), -input.amountCents(), reallocationId, input));
        }
        if (toVault != null) {
            entries.add(entry(workspaceId, toVault.getId(), input.amountCents(), reallocationId, input));
        }
        // Flushed so the balance check below (assertNotOverdrawn re-reads the ledger fresh) sees it.
        vaultEntryRepository.saveAllAndFlush(entries);

        // Only the source can go negative from a reallocation — the destination only ever gains.
        if (fromVault != null) {
            vaultBalanceService.assertNotOverdrawn(workspaceId, fromVault.getId());
        }

        return new VaultReallocationResult(
                reallocationId, accountId, input.fromVaultId(), input.toVaultId(), input.amountCents(), input.date(),
                input.description());
    }

    /**
     * Undoes both sides at once (contract) — a half-undone reallocation would not add up. Re-checks
     * every vault touched afterward: undoing a reallocation can push its destination negative if
     * something else drew on the money since (§4.3's guard applies here just like on create).
     */
    public void delete(UUID workspaceId, UUID reallocationId) {
        List<VaultEntry> entries = vaultEntryRepository.findByReallocationIdAndWorkspaceId(reallocationId, workspaceId);
        if (entries.isEmpty()) {
            throw AppException.vaultReallocationNotFound();
        }
        Set<UUID> vaultIds = entries.stream().map(VaultEntry::getVaultId).collect(Collectors.toSet());
        vaultEntryRepository.deleteAll(entries);
        vaultEntryRepository.flush();
        for (UUID vaultId : vaultIds) {
            vaultBalanceService.assertNotOverdrawn(workspaceId, vaultId);
        }
    }

    /** {@code null} means the unallocated slice; a named vault must exist here and not be archived. */
    private Vault resolveActiveVault(UUID workspaceId, UUID vaultId) {
        if (vaultId == null) {
            return null;
        }
        Vault vault = vaultRepository.findByIdAndWorkspaceId(vaultId, workspaceId).orElseThrow(AppException::vaultNotFound);
        if (vault.isArchived()) {
            throw AppException.vaultArchived();
        }
        return vault;
    }

    private VaultEntry entry(
            UUID workspaceId, UUID vaultId, long signedAmountCents, UUID reallocationId, VaultReallocationInput input) {
        LocalDate date = input.date();
        return VaultEntry.builder()
                .workspaceId(workspaceId)
                .vaultId(vaultId)
                .source(VaultEntrySource.REALLOCATION)
                .amountCents(signedAmountCents)
                .reallocationId(reallocationId)
                .date(date)
                .description(input.description())
                .build();
    }
}
