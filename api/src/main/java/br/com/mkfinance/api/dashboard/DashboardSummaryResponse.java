package br.com.mkfinance.api.dashboard;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.card.StatementResponse;
import java.util.List;

/**
 * Contract schema {@code DashboardSummary}.
 *
 * @param month {@code YYYY-MM}
 * @param totalBalanceCents current balance across accounts, INVESTMENT excluded
 * @param expenseCents by competency — card purchases count on purchase date (R2)
 * @param previousMonthIncomeCents / previousMonthExpenseCents the previous-month comparison
 */
public record DashboardSummaryResponse(
        String month,
        long totalBalanceCents,
        long totalBalanceWithInvestmentsCents,
        long incomeCents,
        long expenseCents,
        Long previousMonthIncomeCents,
        Long previousMonthExpenseCents,
        List<AccountResponse> accounts,
        List<CategoryExpenseResponse> expenseByCategory,
        List<StatementResponse> openStatements) {
}
