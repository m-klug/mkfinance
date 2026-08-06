package br.com.mkfinance.application.vault;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.Vault;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.domain.service.VaultBalanceCalculator;
import br.com.mkfinance.domain.service.VaultBalanceCalculator.Composition;
import br.com.mkfinance.domain.service.VaultBalanceCalculator.Entry;
import br.com.mkfinance.infrastructure.persistence.VaultEntryRepository;
import br.com.mkfinance.infrastructure.persistence.VaultMirrorBucket;
import br.com.mkfinance.infrastructure.persistence.VaultReallocationBucket;
import br.com.mkfinance.infrastructure.persistence.VaultRepository;
import br.com.mkfinance.infrastructure.persistence.VaultYieldBucket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Balance of every vault (DESENVOLVIMENTO-CAIXINHAS.md §4.1–§4.3), owned by BE-9 — the vault
 * counterpart of {@code application/transaction/AccountBalanceService}.
 *
 * <p>Reads the three pre-aggregated {@code group by} buckets described in §5.5's table and feeds
 * them to {@link VaultBalanceCalculator}, so computing every vault's balance in a workspace costs
 * three queries regardless of how long the ledger is — never a sum over a loaded list.
 *
 * <p>Available to any workstream that needs vault balances (BE-10's contribution flow, BE-11's
 * yield split, the vault CRUD/composition endpoints here) — nobody should re-derive this
 * arithmetic, the same rule {@code AccountBalanceService} documents for accounts.
 */
@Service
@RequiredArgsConstructor
public class VaultBalanceService {

    private final VaultEntryRepository vaultEntryRepository;
    private final VaultRepository vaultRepository;

    /** Balance of every vault in the workspace, keyed by vault id — vaults with no ledger history included. */
    @Transactional(readOnly = true)
    public Map<UUID, Balance> balancesByVault(UUID workspaceId) {
        Map<UUID, List<Entry>> entriesByVault = new HashMap<>();

        for (VaultMirrorBucket bucket : vaultEntryRepository.aggregateMirrorBuckets(workspaceId)) {
            entriesByVault
                    .computeIfAbsent(bucket.vaultId(), key -> new ArrayList<>())
                    .add(Entry.mirror(TransactionType.TRANSFER, bucket.status(), bucket.direction(), bucket.amountCents()));
        }
        for (VaultYieldBucket bucket : vaultEntryRepository.aggregateYieldBuckets(workspaceId)) {
            entriesByVault
                    .computeIfAbsent(bucket.vaultId(), key -> new ArrayList<>())
                    .add(Entry.signed(bucket.amountCents(), bucket.status()));
        }
        for (VaultReallocationBucket bucket : vaultEntryRepository.aggregateReallocationBuckets(workspaceId)) {
            // No status column for a reallocation — it always counts as settled (§4.2).
            entriesByVault
                    .computeIfAbsent(bucket.vaultId(), key -> new ArrayList<>())
                    .add(Entry.signed(bucket.amountCents(), TransactionStatus.SETTLED));
        }

        Map<UUID, Balance> balances = new HashMap<>();
        for (Vault vault : vaultRepository.findByWorkspaceId(workspaceId)) {
            List<Entry> entries = entriesByVault.getOrDefault(vault.getId(), List.of());
            balances.put(vault.getId(), VaultBalanceCalculator.balance(entries));
        }
        return balances;
    }

    /** Balance of a single vault; a vault with no ledger history sits at zero. */
    @Transactional(readOnly = true)
    public Balance balanceOf(UUID workspaceId, UUID vaultId) {
        Balance balance = balancesByVault(workspaceId).get(vaultId);
        return balance != null ? balance : new Balance(0L, 0L);
    }

    /**
     * The whole account composition (§4.3): every one of its vaults' balances plus the unallocated
     * slice, with the identity Σ vaults + unallocated = account balance asserted by
     * {@link VaultBalanceCalculator#compose}.
     *
     * @param accountBalance the account's own balance ({@code AccountBalanceService}), passed in
     *     rather than re-derived here so this stays a pure combination of two already-computed facts
     * @param vaults the account's vaults to include (the caller decides archived-or-not)
     */
    @Transactional(readOnly = true)
    public Composition compositionOf(UUID workspaceId, Balance accountBalance, List<Vault> vaults) {
        Map<UUID, Balance> allBalances = balancesByVault(workspaceId);
        Map<UUID, Balance> vaultBalances = new HashMap<>();
        for (Vault vault : vaults) {
            vaultBalances.put(vault.getId(), allBalances.getOrDefault(vault.getId(), new Balance(0L, 0L)));
        }
        return VaultBalanceCalculator.compose(accountBalance, vaultBalances);
    }

    /**
     * §4.3's asymmetric guard: unlike the unallocated slice (only ever shown, never blocked), a
     * vault's settled balance may never go negative. Called at the end of every mutation that
     * touches a vault's ledger — contribution, withdrawal, settle, reallocation — so it reads the
     * ledger's current state fresh rather than trusting a value the caller computed earlier, and
     * therefore works the same regardless of which write path got here. A PENDING movement never
     * trips it by itself, since it does not move {@code currentCents}.
     *
     * @throws AppException VAULT_INSUFFICIENT_BALANCE, carrying how far the vault went under as
     *     {@code shortfallCents} (always positive). It reports the shortfall rather than what was
     *     available because, checking after the write, the pre-write balance is no longer knowable
     *     here without the caller telling us how much it just moved — and the client already knows
     *     the amount it sent, so it can say either "faltam X" or "só há amount − X" from this. The
     *     caller's surrounding {@code @Transactional} unit of work is expected to roll back, so
     *     nothing about the offending write survives regardless of what this reports.
     */
    @Transactional(readOnly = true)
    public void assertNotOverdrawn(UUID workspaceId, UUID vaultId) {
        Balance balance = balanceOf(workspaceId, vaultId);
        if (balance.currentCents() < 0) {
            throw AppException.vaultInsufficientBalance(vaultId, -balance.currentCents());
        }
    }
}
