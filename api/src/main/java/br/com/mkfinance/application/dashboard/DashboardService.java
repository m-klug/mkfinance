package br.com.mkfinance.application.dashboard;

import br.com.mkfinance.application.card.StatementTotals;
import br.com.mkfinance.application.card.StatementTotalsLoader;
import br.com.mkfinance.application.card.StatementView;
import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.transaction.AccountBalanceService;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.Card;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.domain.model.PillarAllocation;
import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.domain.service.PillarAllocationCalculator;
import br.com.mkfinance.domain.service.PillarAllocationCalculator.ContributionEntry;
import br.com.mkfinance.domain.service.PillarAllocationCalculator.ExpenseEntry;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.CardRepository;
import br.com.mkfinance.infrastructure.persistence.PillarAllocationRepository;
import br.com.mkfinance.infrastructure.persistence.StatementRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dashboard's two read models (PLANEJAMENTO §6.6): the pillar allocation report (R2, R9, R10) and
 * the month summary (balances, income/expense, category breakdown, open statements, previous-month
 * comparison).
 *
 * <p>Everything is aggregated in the database via {@link DashboardAggregateRepository} and reuses the
 * balance aggregation of BE-3 ({@link AccountBalanceService}) and the statement-total aggregation of
 * BE-4 ({@link StatementTotalsLoader}). Nothing here loads a workspace's transactions to sum in Java.
 *
 * <p>The allocation math lives in the pure {@link PillarAllocationCalculator}; this service only
 * feeds it the aggregated buckets and the workspace's targets.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardAggregateRepository aggregates;
    private final PillarAllocationRepository pillarAllocationRepository;
    private final AccountRepository accountRepository;
    private final AccountBalanceService accountBalanceService;
    private final CardRepository cardRepository;
    private final StatementRepository statementRepository;
    private final StatementTotalsLoader statementTotalsLoader;
    private final BusinessClock clock;

    /** The requested month, or the current one in America/Sao_Paulo when the client omits it (R7). */
    public YearMonth resolveMonth(YearMonth month) {
        return month != null ? month : YearMonth.from(clock.today());
    }

    /** The pillar allocation report for a month (R2, R9, R10). */
    public PillarAllocationCalculator.Report pillarAllocation(UUID workspaceId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        long income = aggregates.sumIncome(workspaceId, from, to);

        List<ExpenseEntry> expenses = new ArrayList<>();
        for (CategoryPillarSpent row : aggregates.expenseTransactionsByCategory(workspaceId, from, to)) {
            expenses.add(new ExpenseEntry(row.pillar(), row.amountCents(), row.systemCategory()));
        }
        for (CategoryPillarAmount row : aggregates.cardPurchasesByCategory(workspaceId, from, to)) {
            expenses.add(new ExpenseEntry(row.pillar(), row.amountCents(), false));
        }

        List<ContributionEntry> contributions = new ArrayList<>();
        for (PillarContribution row : aggregates.contributionsByPillar(workspaceId, from, to)) {
            contributions.add(new ContributionEntry(row.pillar().toPillar(), row.amountCents()));
        }

        return PillarAllocationCalculator.allocate(income, expenses, contributions, targets(workspaceId));
    }

    /** The month summary, including the comparison with the previous month. */
    public DashboardSummaryView summary(UUID workspaceId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        YearMonth previous = month.minusMonths(1);

        List<AccountBalanceView> accounts = accountBalances(workspaceId);
        List<CategoryPillarAmount> expenseByCategory = expenseByCategory(workspaceId, from, to);
        long expenseCents = expenseByCategory.stream()
                .mapToLong(CategoryPillarAmount::amountCents)
                .sum();

        return new DashboardSummaryView(
                month,
                totalBalance(accounts, true),
                totalBalance(accounts, false),
                aggregates.sumIncome(workspaceId, from, to),
                expenseCents,
                aggregates.sumIncome(workspaceId, previous.atDay(1), previous.atEndOfMonth()),
                monthExpenseCents(workspaceId, previous.atDay(1), previous.atEndOfMonth()),
                accounts,
                expenseByCategory,
                openStatements(workspaceId));
    }

    private Map<Pillar, Integer> targets(UUID workspaceId) {
        Map<Pillar, Integer> targets = new EnumMap<>(Pillar.class);
        for (PillarAllocation allocation : pillarAllocationRepository.findByWorkspaceId(workspaceId)) {
            targets.put(allocation.getId().getPillar(), (int) allocation.getTargetPercent());
        }
        return targets;
    }

    private List<AccountBalanceView> accountBalances(UUID workspaceId) {
        Map<UUID, Balance> balances = accountBalanceService.balancesByAccount(workspaceId);
        List<AccountBalanceView> views = new ArrayList<>();
        for (Account account : accountRepository.findByWorkspaceIdAndArchivedFalseOrderByNameAsc(workspaceId)) {
            Balance balance = balances.getOrDefault(account.getId(), Balance.ofInitial(account.getInitialBalanceCents()));
            views.add(new AccountBalanceView(account, balance));
        }
        return views;
    }

    /** Current balance across the listed accounts; when {@code excludeInvestments}, INVESTMENT is dropped. */
    private long totalBalance(List<AccountBalanceView> accounts, boolean excludeInvestments) {
        return accounts.stream()
                .filter(view -> !(excludeInvestments && view.account().getType() == AccountType.INVESTMENT))
                .mapToLong(view -> view.balance().currentCents())
                .sum();
    }

    /**
     * Spending per category by competency (R2): non-system EXPENSE transactions merged with card
     * purchases on their purchase date. System categories (the statement-payment debit) are excluded
     * so the card is not counted twice. Ordered by amount, largest first.
     */
    private List<CategoryPillarAmount> expenseByCategory(UUID workspaceId, LocalDate from, LocalDate to) {
        Map<UUID, CategoryPillarAmount> byCategory = new LinkedHashMap<>();
        for (CategoryPillarSpent row : aggregates.expenseTransactionsByCategory(workspaceId, from, to)) {
            if (row.systemCategory()) {
                continue; // R2
            }
            merge(byCategory, row.categoryId(), row.pillar(), row.amountCents());
        }
        for (CategoryPillarAmount row : aggregates.cardPurchasesByCategory(workspaceId, from, to)) {
            merge(byCategory, row.categoryId(), row.pillar(), row.amountCents());
        }
        return byCategory.values().stream()
                .sorted(Comparator.comparingLong(CategoryPillarAmount::amountCents).reversed())
                .toList();
    }

    private void merge(Map<UUID, CategoryPillarAmount> byCategory, UUID categoryId, Pillar pillar, long amountCents) {
        byCategory.merge(
                categoryId,
                new CategoryPillarAmount(categoryId, pillar, amountCents),
                (existing, add) -> new CategoryPillarAmount(
                        categoryId, existing.pillar(), existing.amountCents() + add.amountCents()));
    }

    private long monthExpenseCents(UUID workspaceId, LocalDate from, LocalDate to) {
        return expenseByCategory(workspaceId, from, to).stream()
                .mapToLong(CategoryPillarAmount::amountCents)
                .sum();
    }

    /**
     * Bills across every card of the workspace that are not fully paid (remaining &gt; 0), soonest due
     * first — the dashboard's "faturas abertas / próximos vencimentos". Archived cards are included:
     * their statements stay legible and payable (§6.2). Read-only — statements are never materialized
     * here, so opening the dashboard creates no rows.
     */
    private List<StatementView> openStatements(UUID workspaceId) {
        List<UUID> cardIds = cardRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .map(Card::getId)
                .toList();
        if (cardIds.isEmpty()) {
            return List.of();
        }
        List<Statement> statements = statementRepository.findByCardIdIn(cardIds);
        Map<UUID, StatementTotals> totals = statementTotalsLoader.loadFor(statements);
        LocalDate today = clock.today();
        return statements.stream()
                .map(statement -> {
                    StatementTotals total = totals.getOrDefault(statement.getId(), StatementTotals.EMPTY);
                    return new StatementView(
                            statement, statement.effectiveStatus(today), total.totalCents(), total.paidCents());
                })
                .filter(view -> view.totalCents() > 0 && view.paidCents() < view.totalCents())
                .sorted(Comparator.comparing(view -> view.statement().getDueDate()))
                .toList();
    }
}
