package br.com.mkfinance.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.card.CardPurchaseRequest;
import br.com.mkfinance.api.card.CardPurchaseResponse;
import br.com.mkfinance.api.card.CardRequest;
import br.com.mkfinance.api.card.CardResponse;
import br.com.mkfinance.api.card.StatementPaymentRequest;
import br.com.mkfinance.api.card.StatementResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.dashboard.DashboardSummaryResponse;
import br.com.mkfinance.api.dashboard.PillarAllocationLineResponse;
import br.com.mkfinance.api.dashboard.PillarAllocationReportResponse;
import br.com.mkfinance.api.transaction.TransactionRequest;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.api.transaction.TransferRequest;
import br.com.mkfinance.api.transaction.TransferUpdateRequest;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.support.ApiSession;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BE-5 end to end: the dashboard's allocation (R2, R9, R10), summary (balances, income/expense by
 * competency, previous-month comparison, open statements) and the CSV export, driven through the real
 * HTTP + security stack against a real Postgres. Every test registers a fresh user, hence its own
 * isolated workspace with the seeded categories and pillar targets.
 */
class DashboardIntegrationTest extends AbstractIntegrationTest {

    private ApiSession session;
    private UUID workspaceId;

    @BeforeEach
    void registerUser() {
        session = newSession();
        session.primeCsrf();
        CurrentUserResponse user = session.post(
                        "/auth/register",
                        new RegisterRequest("Tester", "dash+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                        CurrentUserResponse.class)
                .getBody();
        workspaceId = user.workspaces().get(0).id();
    }

    @Test
    @DisplayName("canonical scenario: salary + card purchases + aporte + transfer → conferable percents")
    void canonicalAllocationAndSummary() {
        AccountResponse checking = createAccount("Conta", AccountType.CHECKING);
        AccountResponse checking2 = createAccount("Conta 2", AccountType.CHECKING);
        AccountResponse investment = createAccount("Corretora", AccountType.INVESTMENT);

        LocalDate day = LocalDate.of(2026, 3, 15);

        // Income R$ 5.000,00.
        income(checking, incomeCategory(), 500_000L, day);

        // Direct expenses: PLEASURES R$ 250,00, KNOWLEDGE R$ 100,00.
        CategoryResponse pleasures = expenseCategory(Pillar.PLEASURES);
        expense(checking, pleasures, 25_000L, day);
        expense(checking, expenseCategory(Pillar.KNOWLEDGE), 10_000L, day);

        // Card purchases (competency = purchase date, R2): FIXED_COSTS R$ 1.500,00, PLEASURES R$ 500,00.
        CardResponse card = createCard(checking.id());
        cardPurchase(card, expenseCategory(Pillar.FIXED_COSTS), 150_000L, LocalDate.of(2026, 3, 5));
        cardPurchase(card, pleasures, 50_000L, LocalDate.of(2026, 3, 5));

        // Partial statement payment: books a R$ 1.200,00 EXPENSE under the system category — it must
        // be excluded from allocation and from expenseByCategory (R2), and leaves the bill open.
        StatementResponse march = statementOfMonth(card, "2026-03");
        payStatement(march, checking, 120_000L, LocalDate.of(2026, 3, 10));

        // Aportes (R9): GOALS R$ 400,00, FINANCIAL_FREEDOM R$ 1.000,00.
        aporte(checking, investment, 40_000L, ContributionPillar.GOALS, day);
        aporte(checking, investment, 100_000L, ContributionPillar.FINANCIAL_FREEDOM, day);

        // Plain transfer (R4): excluded from everything.
        transfer(checking, checking2, 200_000L, day);

        // --- Allocation (the E3 gate numbers) ---
        PillarAllocationReportResponse allocation = pillarAllocation("2026-03");
        assertThat(allocation.month()).isEqualTo("2026-03");
        assertThat(allocation.incomeCents()).isEqualTo(500_000L);
        assertThat(allocation.noIncome()).isFalse();

        assertPillar(allocation, Pillar.FIXED_COSTS, 30, 150_000L, 30.0);
        assertPillar(allocation, Pillar.COMFORT, 10, 0L, 0.0);
        assertPillar(allocation, Pillar.GOALS, 20, 40_000L, 8.0);
        assertPillar(allocation, Pillar.PLEASURES, 10, 75_000L, 15.0);
        assertPillar(allocation, Pillar.FINANCIAL_FREEDOM, 25, 100_000L, 20.0);
        assertPillar(allocation, Pillar.KNOWLEDGE, 5, 10_000L, 2.0);

        // --- Summary ---
        DashboardSummaryResponse summary = summary("2026-03");
        assertThat(summary.incomeCents()).isEqualTo(500_000L);
        // By competency: 25.000 + 10.000 (direct) + 150.000 + 50.000 (card) = 235.000. The 120.000
        // statement payment is system-category and excluded (R2).
        assertThat(summary.expenseCents()).isEqualTo(235_000L);
        assertThat(summary.expenseByCategory().stream()
                        .mapToLong(c -> c.amountCents())
                        .sum())
                .isEqualTo(235_000L);
        assertThat(summary.expenseByCategory()).noneSatisfy(c -> assertThat(c.amountCents()).isEqualTo(120_000L));

        // Balances (all-time): checking 5.000, checking2 200.000, investment 140.000.
        assertThat(summary.totalBalanceCents()).isEqualTo(205_000L); // INVESTMENT excluded
        assertThat(summary.totalBalanceWithInvestmentsCents()).isEqualTo(345_000L);

        // Previous month (February) had nothing.
        assertThat(summary.previousMonthIncomeCents()).isZero();
        assertThat(summary.previousMonthExpenseCents()).isZero();

        // The partially paid bill is open.
        assertThat(summary.openStatements()).hasSize(1);
        assertThat(summary.openStatements().get(0).totalCents()).isEqualTo(200_000L);
        assertThat(summary.openStatements().get(0).paidCents()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("BE-6 regression: PENDING entries never move the allocation — only SETTLED counts (R5)")
    void pendingEntriesDoNotMoveAllocation() {
        AccountResponse checking = createAccount("Conta", AccountType.CHECKING);
        AccountResponse investment = createAccount("Corretora", AccountType.INVESTMENT);
        LocalDate day = LocalDate.of(2026, 9, 15);

        // Only the SETTLED income should count.
        income(checking, incomeCategory(), 500_000L, day);
        // A PENDING income must not inflate the month's income (sumIncome).
        pendingEntry(checking, incomeCategory(), TransactionType.INCOME, 1_000_000L, day);

        // A PENDING expense must not move its pillar (expenseTransactionsByCategory).
        pendingEntry(checking, expenseCategory(Pillar.PLEASURES), TransactionType.EXPENSE, 250_000L, day);

        // A PENDING contribution must not move its pillar either (contributionsByPillar, R9).
        TransactionResponse outgoing = aporte(checking, investment, 40_000L, ContributionPillar.GOALS, day);
        session.patch(
                path("/transfers/" + outgoing.id()),
                new TransferUpdateRequest(
                        null, 40_000L, day, "aporte", null, TransactionStatus.PENDING, ContributionPillar.GOALS),
                TransactionResponse.class);

        PillarAllocationReportResponse allocation = pillarAllocation("2026-09");
        assertThat(allocation.incomeCents()).isEqualTo(500_000L);
        assertPillar(allocation, Pillar.PLEASURES, 10, 0L, 0.0);
        assertPillar(allocation, Pillar.GOALS, 20, 0L, 0.0);

        // The same PENDING entries must not leak into the summary's income/expense either.
        DashboardSummaryResponse summary = summary("2026-09");
        assertThat(summary.incomeCents()).isEqualTo(500_000L);
        assertThat(summary.expenseByCategory()).isEmpty();
    }

    @Test
    @DisplayName("summary carries the previous month's income and expense")
    void comparesWithThePreviousMonth() {
        AccountResponse checking = createAccount("Conta", AccountType.CHECKING);

        income(checking, incomeCategory(), 300_000L, LocalDate.of(2026, 4, 10));
        expense(checking, expenseCategory(Pillar.FIXED_COSTS), 50_000L, LocalDate.of(2026, 4, 20));

        income(checking, incomeCategory(), 400_000L, LocalDate.of(2026, 5, 10));

        DashboardSummaryResponse may = summary("2026-05");
        assertThat(may.incomeCents()).isEqualTo(400_000L);
        assertThat(may.previousMonthIncomeCents()).isEqualTo(300_000L);
        assertThat(may.previousMonthExpenseCents()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("a month with no income → noIncome flag, null percents, absolute values kept")
    void noIncomeMonth() {
        AccountResponse checking = createAccount("Conta", AccountType.CHECKING);
        // An expense but no income in January.
        expense(checking, expenseCategory(Pillar.KNOWLEDGE), 12_000L, LocalDate.of(2026, 1, 10));

        PillarAllocationReportResponse allocation = pillarAllocation("2026-01");
        assertThat(allocation.noIncome()).isTrue();
        assertThat(allocation.incomeCents()).isZero();
        assertThat(allocation.pillars()).allSatisfy(p -> assertThat(p.actualPercent()).isNull());
        assertThat(line(allocation, Pillar.KNOWLEDGE).spentCents()).isEqualTo(12_000L);
    }

    @Test
    @DisplayName("CSV export: UTF-8 BOM, ';' separator, and honors the from/to filter")
    void csvExport() {
        AccountResponse checking = createAccount("Conta", AccountType.CHECKING);
        income(checking, incomeCategory(), 500_000L, LocalDate.of(2026, 6, 15));
        expense(checking, expenseCategory(Pillar.PLEASURES), 25_000L, LocalDate.of(2026, 6, 16));

        byte[] inRange = session.get(
                        path("/export/transactions.csv?from=2026-06-01&to=2026-06-30"), byte[].class)
                .getBody();

        // UTF-8 BOM so Excel pt-BR reads the encoding.
        assertThat(inRange[0]).isEqualTo((byte) 0xEF);
        assertThat(inRange[1]).isEqualTo((byte) 0xBB);
        assertThat(inRange[2]).isEqualTo((byte) 0xBF);

        String text = new String(inRange, StandardCharsets.UTF_8);
        assertThat(text).contains(";"); // Excel BR separator
        assertThat(text).contains("date;type;direction;description;category;account;amount;status;note");
        assertThat(text).contains("5000,00"); // income amount, comma decimal
        // Two transactions in range → header + 2 data lines.
        assertThat(nonEmptyLines(text)).hasSize(3);

        // A window outside the data returns just the header (the from/to filter is respected).
        byte[] outOfRange = session.get(
                        path("/export/transactions.csv?from=2025-01-01&to=2025-01-31"), byte[].class)
                .getBody();
        assertThat(nonEmptyLines(new String(outOfRange, StandardCharsets.UTF_8))).hasSize(1);
    }

    // --- helpers ---

    private java.util.List<String> nonEmptyLines(String text) {
        return text.lines().filter(l -> !l.isBlank()).toList();
    }

    private String path(String suffix) {
        return "/workspaces/" + workspaceId + suffix;
    }

    private AccountResponse createAccount(String name, AccountType type) {
        return session.post(
                        path("/accounts"),
                        new AccountRequest(name, type, 0L, null, null, null),
                        AccountResponse.class)
                .getBody();
    }

    private CardResponse createCard(UUID paymentAccountId) {
        return session.post(
                        path("/cards"),
                        new CardRequest("Nubank", "VISA", 5_000_00L, (short) 28, (short) 5, paymentAccountId, null),
                        CardResponse.class)
                .getBody();
    }

    private CardPurchaseResponse cardPurchase(
            CardResponse card, CategoryResponse category, long amountCents, LocalDate date) {
        return session.post(
                        path("/cards/" + card.id() + "/purchases"),
                        new CardPurchaseRequest(category.id(), "purchase", amountCents, date, (short) 1),
                        CardPurchaseResponse.class)
                .getBody();
    }

    private StatementResponse statementOfMonth(CardResponse card, String month) {
        StatementResponse[] statements = session.get(
                        path("/cards/" + card.id() + "/statements?from=" + month + "&to=" + month),
                        StatementResponse[].class)
                .getBody();
        return statements[0];
    }

    private void payStatement(StatementResponse statement, AccountResponse account, long amountCents, LocalDate date) {
        session.post(
                path("/statements/" + statement.id() + "/payments"),
                new StatementPaymentRequest(account.id(), amountCents, date),
                StatementResponse.class);
    }

    private TransactionResponse income(
            AccountResponse account, CategoryResponse category, long amountCents, LocalDate date) {
        return entry(account, category, TransactionType.INCOME, amountCents, date);
    }

    private TransactionResponse expense(
            AccountResponse account, CategoryResponse category, long amountCents, LocalDate date) {
        return entry(account, category, TransactionType.EXPENSE, amountCents, date);
    }

    private TransactionResponse entry(
            AccountResponse account, CategoryResponse category, TransactionType type, long amountCents, LocalDate date) {
        return session.post(
                        path("/transactions"),
                        new TransactionRequest(account.id(), category.id(), type, amountCents, date, "entry", null, null, null),
                        TransactionResponse.class)
                .getBody();
    }

    private TransactionResponse pendingEntry(
            AccountResponse account, CategoryResponse category, TransactionType type, long amountCents, LocalDate date) {
        return session.post(
                        path("/transactions"),
                        new TransactionRequest(
                                account.id(),
                                category.id(),
                                type,
                                amountCents,
                                date,
                                "pending entry",
                                null,
                                TransactionStatus.PENDING, null),
                        TransactionResponse.class)
                .getBody();
    }

    private TransactionResponse aporte(
            AccountResponse from, AccountResponse to, long amountCents, ContributionPillar pillar, LocalDate date) {
        return session.post(
                        path("/transfers"),
                        new TransferRequest(from.id(), to.id(), amountCents, date, "aporte", pillar),
                        TransactionResponse.class)
                .getBody();
    }

    private void transfer(AccountResponse from, AccountResponse to, long amountCents, LocalDate date) {
        session.post(
                path("/transfers"),
                new TransferRequest(from.id(), to.id(), amountCents, date, "transfer", null),
                TransactionResponse.class);
    }

    private CategoryResponse incomeCategory() {
        CategoryResponse[] categories =
                session.get(path("/categories?type=INCOME"), CategoryResponse[].class).getBody();
        return Arrays.stream(categories)
                .filter(category -> !category.system())
                .findFirst()
                .orElseThrow();
    }

    private CategoryResponse expenseCategory(Pillar pillar) {
        CategoryResponse[] categories =
                session.get(path("/categories?type=EXPENSE"), CategoryResponse[].class).getBody();
        return Arrays.stream(categories)
                .filter(category -> !category.system() && category.pillar() == pillar)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No seeded category for pillar " + pillar));
    }

    private PillarAllocationReportResponse pillarAllocation(String month) {
        return session.get(path("/dashboard/pillar-allocation?month=" + month), PillarAllocationReportResponse.class)
                .getBody();
    }

    private DashboardSummaryResponse summary(String month) {
        return session.get(path("/dashboard/summary?month=" + month), DashboardSummaryResponse.class)
                .getBody();
    }

    private PillarAllocationLineResponse line(PillarAllocationReportResponse report, Pillar pillar) {
        return report.pillars().stream()
                .filter(p -> p.pillar() == pillar)
                .findFirst()
                .orElseThrow();
    }

    private void assertPillar(
            PillarAllocationReportResponse report,
            Pillar pillar,
            int targetPercent,
            long spentCents,
            double actualPercent) {
        PillarAllocationLineResponse line = line(report, pillar);
        assertThat(line.targetPercent()).isEqualTo(targetPercent);
        assertThat(line.spentCents()).isEqualTo(spentCents);
        assertThat(line.actualPercent()).isEqualTo(actualPercent);
    }
}
