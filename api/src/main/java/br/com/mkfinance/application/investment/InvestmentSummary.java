package br.com.mkfinance.application.investment;

import java.util.List;
import java.util.UUID;

/**
 * Net worth as of today (DESENVOLVIMENTO-POSICOES.md §7). The balance of an account only gains a
 * title's yield when the title is paid, so the balance understates what is owned in the meantime;
 * the estimated net worth adds the yield the active positions have accrued, net of tax.
 *
 * @param accountsAndCashCents balance of the non-archived accounts that are not investment or savings
 * @param investmentsCents balance of the investment and savings accounts plus the accrued net yield of
 *     the active positions
 * @param estimatedNetWorthCents {@code accountsAndCash + investments}
 * @param openStatementsCents what is still owed on card bills; informational, never subtracted
 * @param assetClasses one entry per class of asset; today only fixed income
 * @param accounts the investment and savings accounts, each with how much of it positions explain
 * @param unregisteredInvestmentCents the part of those balances no active position accounts for
 * @param positionsExceedBalance some account has more principal in positions than it holds
 */
public record InvestmentSummary(
        long accountsAndCashCents,
        long investmentsCents,
        long estimatedNetWorthCents,
        long openStatementsCents,
        List<AssetClass> assetClasses,
        List<InvestmentAccount> accounts,
        long unregisteredInvestmentCents,
        boolean positionsExceedBalance) {

    public enum AssetClassCode {
        FIXED_INCOME
    }

    /**
     * @param appliedCents principal of the active positions
     * @param estimatedValueCents what they are worth today, net (a position with no estimate counts at its principal)
     * @param accruedYieldCents {@code estimatedValue − applied}
     * @param positionsWithoutEstimate active positions the app could not value
     */
    public record AssetClass(
            AssetClassCode code,
            long appliedCents,
            long estimatedValueCents,
            long accruedYieldCents,
            int positions,
            int positionsWithoutEstimate) {
    }

    /**
     * @param balanceCents the account's balance in the app
     * @param positionsPrincipalCents principal of the active positions on it
     * @param unregisteredCents {@code balance − positionsPrincipal}, never below zero
     * @param positionsExceedBalance the positions claim more than the account holds: usually a title bought
     *     before the app whose amount was never added to the account's initial balance
     */
    public record InvestmentAccount(
            UUID accountId,
            String name,
            long balanceCents,
            long positionsPrincipalCents,
            long accruedYieldCents,
            long unregisteredCents,
            boolean positionsExceedBalance) {
    }
}
