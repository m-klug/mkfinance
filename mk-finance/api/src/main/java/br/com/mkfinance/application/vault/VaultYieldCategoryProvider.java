package br.com.mkfinance.application.vault;

import br.com.mkfinance.domain.model.Category;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.infrastructure.persistence.CategoryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplies the workspace's system "Rendimento de investimentos" category — the one a distributed
 * yield (DESENVOLVIMENTO-CAIXINHAS.md §4.5) is booked under. Structural copy of
 * {@code application/card/StatementPaymentCategoryProvider}, mirrored for INCOME instead of
 * EXPENSE: the same find-or-create shape, the same {@code uq_category_system_name} (V9) arbitrating
 * a concurrent first booking, and the same reason for existing lazily rather than seeded — the
 * guarantee "this category exists" must hold for workspaces registered before Caixinhas shipped too.
 *
 * <p>The yield really is money that arrived, so it has to be a real INCOME entry to raise the
 * account balance and show in the extrato — but {@code system = true} keeps it out of the month's
 * income (R10's denominator) and the "receitas do mês" tile, both of which already exclude system
 * categories for exactly this reason (§4.0). Unlike the statement-payment category, an INCOME
 * category carries no pillar at all ({@code ck_category_pillar} forbids one), so there is no
 * fallback-pillar concern here.
 */
@Component
@RequiredArgsConstructor
public class VaultYieldCategoryProvider {

    /**
     * Category names are pt-BR <em>data</em> (like the {@code category_template} seed in
     * V2__seed.sql and {@code StatementPaymentCategoryProvider.CATEGORY_NAME}), not UI copy.
     */
    static final String CATEGORY_NAME = "Rendimento de investimentos";

    private final CategoryRepository categoryRepository;

    @Transactional
    public UUID categoryIdFor(UUID workspaceId) {
        return findExisting(workspaceId).orElseGet(() -> create(workspaceId)).getId();
    }

    private java.util.Optional<Category> findExisting(UUID workspaceId) {
        // A workspace holds a couple dozen categories, so filtering the INCOME ones in memory costs
        // nothing and avoids widening the shared CategoryRepository for a single caller — same
        // reasoning as StatementPaymentCategoryProvider.
        return categoryRepository.findByWorkspaceIdAndTypeOrderByNameAsc(workspaceId, CategoryType.INCOME).stream()
                .filter(category -> category.isSystem() && CATEGORY_NAME.equals(category.getName()))
                .findFirst();
    }

    private Category create(UUID workspaceId) {
        Category category = Category.builder()
                .workspaceId(workspaceId)
                .name(CATEGORY_NAME)
                .type(CategoryType.INCOME)
                .pillar(null)
                .system(true)
                .build();
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent first yield booking; uq_category_system_name (V9) kept it
            // to one row, so take the winner's.
            return findExisting(workspaceId).orElseThrow(() -> ex);
        }
    }
}
