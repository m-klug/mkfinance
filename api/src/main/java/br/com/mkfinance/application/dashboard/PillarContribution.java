package br.com.mkfinance.application.dashboard;

import br.com.mkfinance.domain.model.ContributionPillar;

/**
 * One {@code group by contribution_pillar} row of the month's aportes (R9) — the IN legs of
 * transfers into investment/savings accounts, where {@code contribution_pillar} is set exactly once
 * per contribution so the sum counts each one a single time.
 */
public record PillarContribution(ContributionPillar pillar, long amountCents) {
}
