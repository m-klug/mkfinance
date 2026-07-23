package br.com.mkfinance.api.dashboard;

import br.com.mkfinance.api.account.AccountMapper;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.card.CardMapper;
import br.com.mkfinance.api.card.StatementResponse;
import br.com.mkfinance.application.dashboard.DashboardSummaryView;
import br.com.mkfinance.domain.service.PillarAllocationCalculator.Report;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns the dashboard's application views into the contract's DTOs. Reuses BE-2's {@link AccountMapper}
 * and BE-4's {@link CardMapper} so accounts and statements are shaped exactly as their own endpoints
 * shape them — no business decision lives here.
 */
@Component
@RequiredArgsConstructor
public class DashboardMapper {

    private final AccountMapper accountMapper;
    private final CardMapper cardMapper;

    public DashboardSummaryResponse toResponse(DashboardSummaryView view) {
        List<AccountResponse> accounts = view.accounts().stream()
                .map(account -> accountMapper.toResponse(account.account(), account.balance()))
                .toList();
        List<CategoryExpenseResponse> expenseByCategory = view.expenseByCategory().stream()
                .map(row -> new CategoryExpenseResponse(row.categoryId(), row.pillar(), row.amountCents()))
                .toList();
        List<StatementResponse> openStatements =
                view.openStatements().stream().map(cardMapper::toResponse).toList();

        return new DashboardSummaryResponse(
                view.month().toString(),
                view.totalBalanceCents(),
                view.totalBalanceWithInvestmentsCents(),
                view.incomeCents(),
                view.expenseCents(),
                view.previousMonthIncomeCents(),
                view.previousMonthExpenseCents(),
                accounts,
                expenseByCategory,
                openStatements);
    }

    public PillarAllocationReportResponse toResponse(YearMonth month, Report report) {
        List<PillarAllocationLineResponse> pillars = report.pillars().stream()
                .map(line -> new PillarAllocationLineResponse(
                        line.pillar(), line.targetPercent(), line.spentCents(), line.actualPercent()))
                .toList();
        return new PillarAllocationReportResponse(
                month.toString(), report.incomeCents(), report.noIncome(), pillars);
    }
}
