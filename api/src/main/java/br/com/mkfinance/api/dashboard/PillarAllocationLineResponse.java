package br.com.mkfinance.api.dashboard;

import br.com.mkfinance.domain.model.Pillar;

/**
 * One pillar's line in {@code PillarAllocationReport}.
 *
 * @param spentCents expenses by competency + contributions of the pillar (R2, R9)
 * @param actualPercent {@code spent ÷ income × 100}; null when the month had no income (R10)
 */
public record PillarAllocationLineResponse(
        Pillar pillar, int targetPercent, long spentCents, Double actualPercent) {
}
