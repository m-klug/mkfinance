package br.com.mkfinance.application.card;

import br.com.mkfinance.domain.model.Category;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.infrastructure.persistence.CategoryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplies the workspace's system "statement payment" category — the one a statement payment is
 * booked under (R2).
 *
 * <p>The payment has to be a real EXPENSE so it shows in the extrato and moves the account balance,
 * but it must <strong>not</strong> be counted again in reports by category: the spending was already
 * counted on the purchase date. {@code system = true} is that marker, and BE-5's reports exclude it
 * — otherwise every month would show one enormous "statement payment" line next to the categories
 * the money was actually spent on, double-counting the whole card.
 *
 * <p>Created lazily on first payment rather than seeded, so the guarantee "this category exists"
 * holds for workspaces registered before the credit-card feature shipped as well as for new ones,
 * without a backfill migration that a later {@code createWorkspace} would not repeat.
 */
@Component
@RequiredArgsConstructor
public class StatementPaymentCategoryProvider {

    /**
     * Category names are pt-BR <em>data</em> (like the {@code category_template} seed in
     * V2__seed.sql), not UI copy — the rule the backend obeys is that error <em>codes</em> and
     * messages are never localized.
     */
    static final String CATEGORY_NAME = "Pagamento de fatura";

    /**
     * The DB requires a pillar on every EXPENSE category ({@code ck_category_pillar}). The value is
     * inert here — reports skip system categories — so it is set to the least surprising pillar in
     * case it ever surfaces in a raw listing.
     */
    private static final Pillar FALLBACK_PILLAR = Pillar.FIXED_COSTS;

    private final CategoryRepository categoryRepository;

    @Transactional
    public UUID categoryIdFor(UUID workspaceId) {
        return findExisting(workspaceId).orElseGet(() -> create(workspaceId)).getId();
    }

    private java.util.Optional<Category> findExisting(UUID workspaceId) {
        // A workspace holds a couple dozen categories, so filtering the EXPENSE ones in memory costs
        // nothing and avoids widening the shared CategoryRepository for a single caller.
        return categoryRepository.findByWorkspaceIdAndTypeOrderByNameAsc(workspaceId, CategoryType.EXPENSE).stream()
                .filter(category -> category.isSystem() && CATEGORY_NAME.equals(category.getName()))
                .findFirst();
    }

    private Category create(UUID workspaceId) {
        Category category = Category.builder()
                .workspaceId(workspaceId)
                .name(CATEGORY_NAME)
                .type(CategoryType.EXPENSE)
                .pillar(FALLBACK_PILLAR)
                .system(true)
                .build();
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent first payment; uq_category_system_name (V9) kept it to
            // one row, so take the winner's.
            return findExisting(workspaceId).orElseThrow(() -> ex);
        }
    }
}
