package br.com.mkfinance.api.investment;

import br.com.mkfinance.application.investment.InvestmentSummary;
import java.util.List;
import java.util.UUID;

/**
 * Contract schema {@code InvestmentSummary}: net worth as of today, in cents. See
 * {@link InvestmentSummary} for how each figure is built.
 */
public record InvestmentSummaryResponse(
        long accountsAndCashCents,
        long investmentsCents,
        long estimatedNetWorthCents,
        long openStatementsCents,
        List<AssetClass> assetClasses,
        List<InvestmentAccount> accounts,
        long unregisteredInvestmentCents,
        boolean positionsExceedBalance) {

    public record AssetClass(
            String code,
            long appliedCents,
            long estimatedValueCents,
            long accruedYieldCents,
            int positions,
            int positionsWithoutEstimate) {
    }

    public record InvestmentAccount(
            UUID accountId,
            String name,
            long balanceCents,
            long positionsPrincipalCents,
            long accruedYieldCents,
            long unregisteredCents,
            boolean positionsExceedBalance) {
    }

    public static InvestmentSummaryResponse of(InvestmentSummary summary) {
        return new InvestmentSummaryResponse(
                summary.accountsAndCashCents(),
                summary.investmentsCents(),
                summary.estimatedNetWorthCents(),
                summary.openStatementsCents(),
                summary.assetClasses().stream()
                        .map(c -> new AssetClass(c.code().name(), c.appliedCents(), c.estimatedValueCents(),
                                c.accruedYieldCents(), c.positions(), c.positionsWithoutEstimate()))
                        .toList(),
                summary.accounts().stream()
                        .map(a -> new InvestmentAccount(a.accountId(), a.name(), a.balanceCents(),
                                a.positionsPrincipalCents(), a.accruedYieldCents(), a.unregisteredCents(),
                                a.positionsExceedBalance()))
                        .toList(),
                summary.unregisteredInvestmentCents(),
                summary.positionsExceedBalance());
    }
}
