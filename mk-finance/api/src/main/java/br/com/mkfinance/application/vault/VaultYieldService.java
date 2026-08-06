package br.com.mkfinance.application.vault;

import br.com.mkfinance.application.transaction.TransactionInput;
import br.com.mkfinance.application.transaction.TransactionService;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.Vault;
import br.com.mkfinance.domain.model.VaultEntry;
import br.com.mkfinance.domain.model.VaultEntrySource;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.domain.service.YieldDistributor;
import br.com.mkfinance.domain.service.YieldDistributor.Share;
import br.com.mkfinance.domain.service.YieldDistributor.VaultBase;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import br.com.mkfinance.infrastructure.persistence.VaultEntryRepository;
import br.com.mkfinance.infrastructure.persistence.VaultRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Books the period's investment yield (DESENVOLVIMENTO-CAIXINHAS.md §4.5, contract
 * {@code createVaultYield}): one INCOME transaction under the system "Rendimento de investimentos"
 * category, split across the account's active vaults in proportion to their settled balance
 * ({@link YieldDistributor}), all in a single unit of work.
 *
 * <p><strong>INCOME, never a new transaction type</strong> — the money really did arrive, so the
 * account balance has to rise the same way any other income does; a new type would mean touching
 * {@code BalanceCalculator}, {@code TransactionType} and {@code ck_transaction_category}, which is
 * exactly the regression §4.5 rules out. Being INCOME and never TRANSFER, it consumes no pillar:
 * {@code contributionsByPillar} only reads the IN leg of a TRANSFER, so a yield transaction is
 * invisible to it by construction and {@code PillarAllocationCalculator} needs no change at all.
 *
 * <p>Reuses {@link TransactionService#create} rather than inserting into {@code transaction}
 * directly — the same discipline {@code TransactionService#createSettledExpense} documents for
 * BE-4's statement payments, one level up: the booked entry has to behave like any other transaction
 * (show in the extrato, move the account balance, be editable/deletable through the normal
 * endpoints, including {@code DELETE /transactions/{id}} cascading the split away) rather than being
 * a parallel code path that could drift from it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VaultYieldService {

    private final VaultService vaultService;
    private final VaultRepository vaultRepository;
    private final VaultEntryRepository vaultEntryRepository;
    private final VaultBalanceService vaultBalanceService;
    private final VaultYieldCategoryProvider categoryProvider;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    /**
     * @throws br.com.mkfinance.domain.exception.AppException ACCOUNT_NOT_FOUND when {@code accountId}
     *     is unknown or belongs to another workspace (R8)
     */
    public VaultYieldResult create(UUID workspaceId, UUID accountId, VaultYieldInput input) {
        Account account = vaultService.ownedAccount(workspaceId, accountId);
        UUID categoryId = categoryProvider.categoryIdFor(workspaceId);

        Transaction transaction = transactionService.create(
                workspaceId,
                new TransactionInput(
                        account.getId(),
                        categoryId,
                        TransactionType.INCOME,
                        input.amountCents(),
                        input.date(),
                        description(input),
                        null,
                        null,
                        null));
        // vault_entry.transaction_id's FK needs the row to already exist before the split below
        // inserts against it — the same saveAndFlush-before-vaultLinkService.link discipline
        // TransferService follows (armadilha 5, DESENVOLVIMENTO-CAIXINHAS.md §7), one call flatter
        // since TransactionService#create already issued the save.
        transactionRepository.flush();

        List<Share> shares = YieldDistributor.distribute(input.amountCents(), basesOf(workspaceId, account.getId()));
        List<VaultEntry> entries = shares.stream().map(share -> entry(workspaceId, transaction.getId(), share)).toList();
        // A yield share is always positive — it can never overdraw a vault, so unlike every other
        // vault_entry writer here there is no assertNotOverdrawn call: §4.3's guard only ever fires
        // on money leaving.
        vaultEntryRepository.saveAll(entries);

        return new VaultYieldResult(transaction.getId(), input.amountCents(), input.date(), shares);
    }

    /** Every active vault of the account, based on its settled ("current") balance (§4.5's base_i). */
    private List<VaultBase> basesOf(UUID workspaceId, UUID accountId) {
        List<Vault> vaults = vaultRepository.findByAccountIdAndArchivedFalseOrderByNameAsc(accountId);
        if (vaults.isEmpty()) {
            return List.of();
        }
        Map<UUID, Balance> balances = vaultBalanceService.balancesByVault(workspaceId);
        return vaults.stream()
                .map(vault -> new VaultBase(
                        vault.getId(),
                        vault.getName(),
                        balances.getOrDefault(vault.getId(), new Balance(0L, 0L)).currentCents()))
                .toList();
    }

    private VaultEntry entry(UUID workspaceId, UUID transactionId, Share share) {
        return VaultEntry.builder()
                .workspaceId(workspaceId)
                .vaultId(share.vaultId())
                .source(VaultEntrySource.YIELD)
                .transactionId(transactionId)
                .amountCents(share.amountCents())
                .build();
    }

    /** {@code transaction.description} is {@code NOT NULL}; the category's own name is the fallback. */
    private String description(VaultYieldInput input) {
        return input.description() != null && !input.description().isBlank()
                ? input.description()
                : VaultYieldCategoryProvider.CATEGORY_NAME;
    }
}
