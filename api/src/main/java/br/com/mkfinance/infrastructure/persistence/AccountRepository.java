package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    List<Account> findByWorkspaceIdAndArchivedFalseOrderByNameAsc(UUID workspaceId);

    Optional<Account> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /**
     * The {@code transaction} table belongs to BE-3 (no JPA entity here yet); this native
     * existence check is the delete guard required from day 1 (ACCOUNT_HAS_TRANSACTIONS) even
     * though no transactions exist until BE-3 lands.
     */
    @Query(value = "select exists(select 1 from transaction where account_id = :accountId)", nativeQuery = true)
    boolean existsTransactionForAccount(@Param("accountId") UUID accountId);
}
