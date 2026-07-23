package br.com.mkfinance.application.dashboard;

import br.com.mkfinance.domain.model.Pillar;
import java.util.UUID;

/**
 * One {@code (category, pillar, summed cents)} row. Used both for card purchases aggregated by
 * competency (their purchase date, R2) and, after merging with non-system expenses, for the
 * dashboard's spending-by-category breakdown.
 */
public record CategoryPillarAmount(UUID categoryId, Pillar pillar, long amountCents) {
}
