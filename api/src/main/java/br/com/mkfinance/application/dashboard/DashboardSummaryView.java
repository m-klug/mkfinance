package br.com.mkfinance.application.dashboard;

import br.com.mkfinance.application.card.StatementView;
import java.time.YearMonth;
import java.util.List;

/**
 * Everything the dashboard summary shows, already computed (PLANEJAMENTO §6.6). The API mapper turns
 * this into the contract's {@code DashboardSummary} without any further business decision.
 *
 * @param totalBalanceCents current balance across accounts, INVESTMENT excluded (contract)
 * @param totalBalanceWithInvestmentsCents current balance across every account
 * @param incomeCents the month's income (R4: transfers excluded)
 * @param expenseCents the month's expense by competency (R2: card purchases on their purchase date,
 *     statement-payment debit excluded)
 * @param previousMonthIncomeCents same for the previous month — the comparison
 * @param expenseByCategory spending per category, system categories excluded (R2)
 * @param openStatements bills across the workspace's cards that are not fully paid
 */
public record DashboardSummaryView(
        YearMonth month,
        long totalBalanceCents,
        long totalBalanceWithInvestmentsCents,
        long incomeCents,
        long expenseCents,
        Long previousMonthIncomeCents,
        Long previousMonthExpenseCents,
        List<AccountBalanceView> accounts,
        List<CategoryPillarAmount> expenseByCategory,
        List<StatementView> openStatements) {
}
