package br.com.mkfinance.application.vault;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.Vault;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.VaultEntryRepository;
import br.com.mkfinance.infrastructure.persistence.VaultRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD of {@link Vault} (DESENVOLVIMENTO-CAIXINHAS.md §4, contract tag {@code vaults}), owned by
 * BE-9. Every write here goes through {@link #getOwned}/{@code ownedAccount} so R8 (workspace
 * isolation) holds on every path, the same discipline {@code AccountService}/{@code TagService}
 * follow for their own resources.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VaultService {

    private final VaultRepository vaultRepository;
    private final VaultEntryRepository vaultEntryRepository;
    private final AccountRepository accountRepository;
    private final VaultBalanceService vaultBalanceService;

    @Transactional(readOnly = true)
    public List<Vault> list(UUID workspaceId, UUID accountId, boolean includeArchived) {
        Account account = ownedAccount(workspaceId, accountId);
        return includeArchived
                ? vaultRepository.findByAccountIdOrderByNameAsc(account.getId())
                : vaultRepository.findByAccountIdAndArchivedFalseOrderByNameAsc(account.getId());
    }

    /**
     * Only INVESTMENT/SAVINGS accounts take vaults (R9) — anything else is VALIDATION_ERROR, the
     * same treatment as the reallocation with both sides null (CT-2 decision 5).
     */
    public Vault create(UUID workspaceId, UUID accountId, VaultInput input) {
        Account account = ownedAccount(workspaceId, accountId);
        requireEligibleAccountType(account);
        requireGoalFieldsConsistent(input.targetAmountCents(), input.targetDate());
        String name = requireName(input.name());
        ensureNameAvailable(account.getId(), name, null);

        Vault vault = Vault.builder()
                .workspaceId(workspaceId)
                .accountId(account.getId())
                .name(name)
                .contributionPillar(input.contributionPillar())
                .targetAmountCents(input.targetAmountCents())
                .targetDate(input.targetDate())
                .color(input.color())
                .archived(false)
                .build();
        return vaultRepository.save(vault);
    }

    /**
     * A {@code contributionPillar} change applies from here on — contributions already booked keep
     * the pillar they consumed (competency doctrine, §4.4's contract description). The account type
     * is not re-checked: it cannot have changed since {@link #create} already required it.
     */
    public Vault update(UUID workspaceId, UUID vaultId, VaultInput input) {
        Vault vault = getOwned(workspaceId, vaultId);
        requireGoalFieldsConsistent(input.targetAmountCents(), input.targetDate());
        String name = requireName(input.name());
        ensureNameAvailable(vault.getAccountId(), name, vault);

        vault.setName(name);
        vault.setContributionPillar(input.contributionPillar());
        vault.setTargetAmountCents(input.targetAmountCents());
        vault.setTargetDate(input.targetDate());
        vault.setColor(input.color());
        return vaultRepository.save(vault);
    }

    /**
     * VAULT_NOT_EMPTY unless both balances are zero (§4.7) — idempotent, mirrors
     * {@code AccountService.archive}: archiving an already-archived vault just returns it.
     */
    public Vault archive(UUID workspaceId, UUID vaultId) {
        Vault vault = getOwned(workspaceId, vaultId);
        if (vault.isArchived()) {
            return vault;
        }
        Balance balance = vaultBalanceService.balanceOf(workspaceId, vaultId);
        if (balance.currentCents() != 0L || balance.projectedCents() != 0L) {
            throw AppException.vaultNotEmpty(balance.currentCents());
        }
        vault.setArchived(true);
        return vaultRepository.save(vault);
    }

    /** VAULT_HAS_ENTRIES when anything was ever booked to it (§4.7) — archive it instead. */
    public void delete(UUID workspaceId, UUID vaultId) {
        Vault vault = getOwned(workspaceId, vaultId);
        if (vaultEntryRepository.existsByVaultId(vault.getId())) {
            throw AppException.vaultHasEntries();
        }
        vaultRepository.delete(vault);
    }

    @Transactional(readOnly = true)
    public Vault getOwned(UUID workspaceId, UUID vaultId) {
        return vaultRepository.findByIdAndWorkspaceId(vaultId, workspaceId).orElseThrow(AppException::vaultNotFound);
    }

    @Transactional(readOnly = true)
    public Account ownedAccount(UUID workspaceId, UUID accountId) {
        return accountRepository
                .findByIdAndWorkspaceId(accountId, workspaceId)
                .orElseThrow(AppException::accountNotFound);
    }

    private void requireEligibleAccountType(Account account) {
        if (account.getType() != AccountType.INVESTMENT && account.getType() != AccountType.SAVINGS) {
            throw VaultValidationException.of("accountId", "ACCOUNT_TYPE_NOT_ELIGIBLE_FOR_VAULTS");
        }
    }

    /** OpenAPI: "Sending [targetDate] without targetAmountCents answers VALIDATION_ERROR." */
    private void requireGoalFieldsConsistent(Long targetAmountCents, java.time.LocalDate targetDate) {
        if (targetDate != null && targetAmountCents == null) {
            throw VaultValidationException.of("targetDate", "TARGET_DATE_REQUIRES_TARGET_AMOUNT");
        }
    }

    private String requireName(String rawName) {
        String trimmed = rawName == null ? "" : rawName.trim();
        if (trimmed.isEmpty()) {
            throw VaultValidationException.of("name", "NOT_BLANK");
        }
        return trimmed;
    }

    /** Vault names are unique per account, compared case-insensitively (§4.0, ux_vault_account_name_ci). */
    private void ensureNameAvailable(UUID accountId, String name, Vault self) {
        boolean sameAsSelf = self != null && name.equalsIgnoreCase(self.getName());
        if (!sameAsSelf && vaultRepository.existsByAccountIdAndNameIgnoreCase(accountId, name)) {
            throw AppException.vaultNameDuplicate(name);
        }
    }
}
