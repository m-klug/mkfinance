package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Vault;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultRepository extends JpaRepository<Vault, UUID> {

    Optional<Vault> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Every vault of the workspace — what {@code VaultBalanceService} iterates to compose balances. */
    List<Vault> findByWorkspaceId(UUID workspaceId);

    List<Vault> findByAccountIdOrderByNameAsc(UUID accountId);

    List<Vault> findByAccountIdAndArchivedFalseOrderByNameAsc(UUID accountId);

    /** {@code Account.hasVaults} (CT-2) — an account has vaults once it has at least one non-archived one. */
    boolean existsByAccountIdAndArchivedFalse(UUID accountId);

    /** Vault names are unique per account, compared case-insensitively (§4.0, ux_vault_account_name_ci). */
    boolean existsByAccountIdAndNameIgnoreCase(UUID accountId, String name);
}
