package br.com.mkfinance.application.dashboard;

import br.com.mkfinance.domain.model.Pillar;
import java.util.UUID;

/**
 * One {@code group by (category, pillar, system)} row of the month's EXPENSE transactions. The
 * {@code systemCategory} flag is what lets the allocation report drop the statement-payment debit
 * (R2): its spending was already counted on the purchase date.
 */
public record CategoryPillarSpent(UUID categoryId, Pillar pillar, boolean systemCategory, long amountCents) {
}
