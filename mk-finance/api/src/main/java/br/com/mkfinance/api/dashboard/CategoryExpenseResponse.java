package br.com.mkfinance.api.dashboard;

import br.com.mkfinance.domain.model.Pillar;
import java.util.UUID;

/** One entry of {@code DashboardSummary.expenseByCategory} — spending in a category by competency (R2). */
public record CategoryExpenseResponse(UUID categoryId, Pillar pillar, long amountCents) {
}
