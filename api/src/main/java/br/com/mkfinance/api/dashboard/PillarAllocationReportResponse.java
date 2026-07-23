package br.com.mkfinance.api.dashboard;

import java.util.List;

/**
 * Contract schema {@code PillarAllocationReport}.
 *
 * @param noIncome true when the month's income is zero — percents are null and the absolute
 *     {@code spentCents} should be shown instead (R10)
 */
public record PillarAllocationReportResponse(
        String month, long incomeCents, boolean noIncome, List<PillarAllocationLineResponse> pillars) {
}
