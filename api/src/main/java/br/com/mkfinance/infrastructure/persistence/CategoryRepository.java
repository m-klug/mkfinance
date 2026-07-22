package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Category;
import br.com.mkfinance.domain.model.CategoryType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    List<Category> findByWorkspaceIdAndTypeOrderByNameAsc(UUID workspaceId, CategoryType type);

    Optional<Category> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query(value = "select exists(select 1 from category where parent_id = :categoryId)", nativeQuery = true)
    boolean existsChildCategory(@Param("categoryId") UUID categoryId);

    /** {@code transaction}/{@code card_purchase} belong to BE-3/BE-4 — see AccountRepository note. */
    @Query(value = "select exists(select 1 from transaction where category_id = :categoryId)", nativeQuery = true)
    boolean existsTransactionForCategory(@Param("categoryId") UUID categoryId);

    @Query(value = "select exists(select 1 from card_purchase where category_id = :categoryId)", nativeQuery = true)
    boolean existsCardPurchaseForCategory(@Param("categoryId") UUID categoryId);
}
