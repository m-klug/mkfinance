package br.com.mkfinance.reimbursement;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.dashboard.DashboardSummaryResponse;
import br.com.mkfinance.api.dashboard.PillarAllocationReportResponse;
import br.com.mkfinance.api.reimbursement.ReimbursementRequest;
import br.com.mkfinance.api.reimbursement.ReimbursementResponse;
import br.com.mkfinance.api.transaction.TransactionPageResponse;
import br.com.mkfinance.api.transaction.TransactionRequest;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.support.ApiSession;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * ADR-0002 end to end: a shared boleto reimbursed late by a PIX and by cash, driven through the real
 * HTTP + security stack against a real Postgres. The scenario is the one the feature exists for.
 */
class ReimbursementIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate MAY = LocalDate.of(2026, 5, 10);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 20);

    private ApiSession session;
    private UUID workspaceId;
    private AccountResponse checking;
    private AccountResponse cash;

    @BeforeEach
    void registerUser() {
        session = newSession();
        session.primeCsrf();
        CurrentUserResponse user = session.post(
                        "/auth/register",
                        new RegisterRequest("Tester", "reimb+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                        CurrentUserResponse.class)
                .getBody();
        workspaceId = user.workspaces().get(0).id();
        checking = createAccount("Conta", AccountType.CHECKING);
        cash = createAccount("Carteira", AccountType.CASH);
    }

    @Test
    @DisplayName("boleto 2.000, 1.500 reimbursable: pilar and income follow the links, at the expense's month")
    void sharedBoletoReimbursedLate() {
        CategoryResponse fixed = expenseCategory(Pillar.FIXED_COSTS);
        CategoryResponse incomeCat = incomeCategory();
        entry(checking, incomeCat, TransactionType.INCOME, 1_000_000L, MAY, "Salario", null, null);
        entry(checking, incomeCat, TransactionType.INCOME, 1_000_000L, JULY, "Salario", null, null);
        TransactionResponse boleto =
                entry(checking, fixed, TransactionType.EXPENSE, 200_000L, MAY, "Boleto", null, 150_000L);

        assertThat(boleto.reimbursableCents()).isEqualTo(150_000L);
        assertThat(boleto.reimbursedCents()).isZero();
        assertThat(boleto.pendingReimbursementCents()).isEqualTo(150_000L);
        assertThat(boleto.effectiveAmountCents()).isEqualTo(200_000L);
        assertThat(fixedSpent("2026-05")).isEqualTo(200_000L); // cheio ate o dinheiro chegar

        // Two months late, a PIX of 500 and cash of 1.000 arrive.
        TransactionResponse pix = entry(checking, incomeCat, TransactionType.INCOME, 50_000L, JULY, "PIX Fulano", null, null);
        TransactionResponse envelope = entry(cash, incomeCat, TransactionType.INCOME, 100_000L, JULY, "Dinheiro Ciclano", null, null);
        // Before linking, PIX and cash are plain income.
        assertThat(summary("2026-07").incomeCents()).isEqualTo(1_150_000L);

        link(pix, boleto, 50_000L);
        assertThat(fixedSpent("2026-05")).isEqualTo(150_000L);
        assertThat(allocation("2026-05").incomeCents()).isEqualTo(1_000_000L);
        assertThat(summary("2026-07").incomeCents()).isEqualTo(1_000_000L + 100_000L);

        link(envelope, boleto, 100_000L);
        assertThat(fixedSpent("2026-05")).isEqualTo(50_000L);
        assertThat(summary("2026-07").incomeCents()).isEqualTo(1_000_000L);
        assertThat(summary("2026-05").expenseCents()).isEqualTo(50_000L);

        // Balances are untouched: the money really left and really came back.
        assertThat(balance(checking)).isEqualTo(1_000_000L + 1_000_000L - 200_000L + 50_000L);
        assertThat(balance(cash)).isEqualTo(100_000L);

        TransactionResponse reloaded = transaction(boleto.id());
        assertThat(reloaded.reimbursedCents()).isEqualTo(150_000L);
        assertThat(reloaded.pendingReimbursementCents()).isZero();
        assertThat(reloaded.effectiveAmountCents()).isEqualTo(50_000L);
        assertThat(session.get(path("/transactions/reimbursable-pending"), TransactionResponse[].class).getBody())
                .isEmpty();
        assertThat(session.get(path("/transactions/" + boleto.id() + "/reimbursements"), ReimbursementResponse[].class)
                        .getBody())
                .hasSize(2);
    }

    @Test
    @DisplayName("one income can cover several expenses; linking the same pair again adds to it")
    void partialAndRepeatedLinks() {
        CategoryResponse fixed = expenseCategory(Pillar.FIXED_COSTS);
        TransactionResponse a = entry(checking, fixed, TransactionType.EXPENSE, 100_000L, MAY, "A", null, 60_000L);
        TransactionResponse b = entry(checking, fixed, TransactionType.EXPENSE, 100_000L, MAY, "B", null, 60_000L);
        TransactionResponse pix = entry(checking, incomeCategory(), TransactionType.INCOME, 100_000L, JULY, "PIX", null, null);

        link(pix, a, 40_000L);
        ReimbursementResponse merged = link(pix, a, 20_000L);
        assertThat(merged.amountCents()).isEqualTo(60_000L);
        link(pix, b, 40_000L);

        assertThat(transaction(pix.id()).reimbursedCents()).isEqualTo(100_000L);
        assertThat(fixedSpent("2026-05")).isEqualTo(200_000L - 100_000L);
        TransactionResponse[] pending =
                session.get(path("/transactions/reimbursable-pending"), TransactionResponse[].class).getBody();
        assertThat(pending).extracting(TransactionResponse::id).containsExactly(b.id());
        assertThat(pending[0].pendingReimbursementCents()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("links are refused past the income, past the expense's reimbursable, and for unsettled or wrong-typed rows")
    void linkValidation() {
        CategoryResponse fixed = expenseCategory(Pillar.FIXED_COSTS);
        TransactionResponse expense = entry(checking, fixed, TransactionType.EXPENSE, 100_000L, MAY, "E", null, 50_000L);
        TransactionResponse plain = entry(checking, fixed, TransactionType.EXPENSE, 100_000L, MAY, "Plain", null, null);
        TransactionResponse pix = entry(checking, incomeCategory(), TransactionType.INCOME, 30_000L, JULY, "PIX", null, null);
        TransactionResponse pendingIncome = entry(
                checking, incomeCategory(), TransactionType.INCOME, 30_000L, JULY, "Futuro", TransactionStatus.PENDING, null);

        assertThat(tryLink(pix, expense, 30_001L)).isEqualTo(ErrorCode.REIMBURSEMENT_OVER_INCOME);
        assertThat(tryLink(pix, plain, 1L)).isEqualTo(ErrorCode.REIMBURSEMENT_OVER_EXPENSE);
        assertThat(tryLink(pendingIncome, expense, 1_000L)).isEqualTo(ErrorCode.REIMBURSEMENT_INVALID_LINK);
        assertThat(tryLink(expense, plain, 1_000L)).isEqualTo(ErrorCode.REIMBURSEMENT_INVALID_LINK);

        link(pix, expense, 30_000L);
        TransactionResponse pix2 = entry(checking, incomeCategory(), TransactionType.INCOME, 30_000L, JULY, "PIX 2", null, null);
        assertThat(tryLink(pix2, expense, 20_001L)).isEqualTo(ErrorCode.REIMBURSEMENT_OVER_EXPENSE);
    }

    @Test
    @DisplayName("a linked row cannot be deleted, retyped or edited below the link; unlinking restores the pilar")
    void linkedRowsAreProtected() {
        CategoryResponse fixed = expenseCategory(Pillar.FIXED_COSTS);
        TransactionResponse expense = entry(checking, fixed, TransactionType.EXPENSE, 100_000L, MAY, "E", null, 50_000L);
        TransactionResponse pix = entry(checking, incomeCategory(), TransactionType.INCOME, 50_000L, JULY, "PIX", null, null);
        ReimbursementResponse link = link(pix, expense, 50_000L);

        assertThat(errorCode(session.delete(path("/transactions/" + expense.id()), Map.class)))
                .isEqualTo(ErrorCode.TRANSACTION_HAS_REIMBURSEMENTS);
        assertThat(errorCode(session.delete(path("/transactions/" + pix.id()), Map.class)))
                .isEqualTo(ErrorCode.TRANSACTION_HAS_REIMBURSEMENTS);

        // Lowering the reimbursable below what is linked.
        assertThat(errorCode(patch(expense, 100_000L, 40_000L))).isEqualTo(ErrorCode.TRANSACTION_HAS_REIMBURSEMENTS);
        // Clearing it.
        assertThat(errorCode(patch(expense, 100_000L, 0L))).isEqualTo(ErrorCode.TRANSACTION_HAS_REIMBURSEMENTS);
        // Lowering the income's amount below the link.
        assertThat(errorCode(patch(pix, 40_000L, null))).isEqualTo(ErrorCode.TRANSACTION_HAS_REIMBURSEMENTS);
        // Editing the description alone (reimbursable omitted = left alone) is fine.
        assertThat(patch(expense, 100_000L, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transaction(expense.id()).reimbursableCents()).isEqualTo(50_000L);

        assertThat(fixedSpent("2026-05")).isEqualTo(50_000L);
        assertThat(session.delete(path("/reimbursements/" + link.id()), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(fixedSpent("2026-05")).isEqualTo(100_000L);
        assertThat(summary("2026-07").incomeCents()).isEqualTo(50_000L);

        assertThat(session.delete(path("/transactions/" + pix.id()), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("reimbursableCents is only valid on an expense, within 1..amount")
    void reimbursableValidation() {
        CategoryResponse fixed = expenseCategory(Pillar.FIXED_COSTS);
        ResponseEntity<Map> tooBig = post(checking, fixed, TransactionType.EXPENSE, 100_000L, 100_001L);
        assertThat(errorCode(tooBig)).isEqualTo(ErrorCode.REIMBURSABLE_INVALID);
        ResponseEntity<Map> onIncome = post(checking, incomeCategory(), TransactionType.INCOME, 100_000L, 10_000L);
        assertThat(errorCode(onIncome)).isEqualTo(ErrorCode.REIMBURSABLE_INVALID);
    }

    @Test
    @DisplayName("another workspace cannot see or link this workspace's transactions")
    void workspaceIsolation() {
        TransactionResponse expense = entry(
                checking, expenseCategory(Pillar.FIXED_COSTS), TransactionType.EXPENSE, 100_000L, MAY, "E", null, 50_000L);
        TransactionResponse pix = entry(checking, incomeCategory(), TransactionType.INCOME, 50_000L, JULY, "PIX", null, null);
        link(pix, expense, 10_000L);

        ApiSession other = newSession();
        other.primeCsrf();
        UUID otherWorkspace = other.post(
                        "/auth/register",
                        new RegisterRequest("Other", "reimb-other+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                        CurrentUserResponse.class)
                .getBody()
                .workspaces()
                .get(0)
                .id();
        ResponseEntity<Map> cross = other.post(
                "/workspaces/" + otherWorkspace + "/transactions/" + pix.id() + "/reimbursements",
                new ReimbursementRequest(expense.id(), 1_000L),
                Map.class);
        assertThat(errorCode(cross)).isEqualTo(ErrorCode.TRANSACTION_NOT_FOUND);
        assertThat(other.get(
                                "/workspaces/" + otherWorkspace + "/transactions/reimbursable-pending",
                                TransactionResponse[].class)
                        .getBody())
                .isEmpty();
    }

    // -- helpers --------------------------------------------------------------------------------

    private String path(String suffix) {
        return "/workspaces/" + workspaceId + suffix;
    }

    private AccountResponse createAccount(String name, AccountType type) {
        return session.post(path("/accounts"), new AccountRequest(name, type, 0L, null, null, null), AccountResponse.class)
                .getBody();
    }

    private long balance(AccountResponse account) {
        return Arrays.stream(session.get(path("/accounts"), AccountResponse[].class).getBody())
                .filter(a -> a.id().equals(account.id()))
                .findFirst()
                .orElseThrow()
                .balanceCents();
    }

    private TransactionResponse entry(
            AccountResponse account,
            CategoryResponse category,
            TransactionType type,
            long amountCents,
            LocalDate date,
            String description,
            TransactionStatus status,
            Long reimbursableCents) {
        return session.post(
                        path("/transactions"),
                        new TransactionRequest(
                                account.id(), category.id(), type, amountCents, date, description, null, status, null, reimbursableCents),
                        TransactionResponse.class)
                .getBody();
    }

    private ResponseEntity<Map> post(
            AccountResponse account, CategoryResponse category, TransactionType type, long amountCents, Long reimbursable) {
        return session.post(
                path("/transactions"),
                new TransactionRequest(account.id(), category.id(), type, amountCents, MAY, "x", null, null, null, reimbursable),
                Map.class);
    }

    private ResponseEntity<Map> patch(TransactionResponse tx, long amountCents, Long reimbursable) {
        return session.patch(
                path("/transactions/" + tx.id()),
                new TransactionRequest(
                        tx.accountId(), tx.categoryId(), tx.type(), amountCents, tx.date(), tx.description() + "!", null, null, null, reimbursable),
                Map.class);
    }

    private TransactionResponse transaction(UUID id) {
        return session.get(path("/transactions?size=200"), TransactionPageResponse.class).getBody().content().stream()
                .filter(t -> t.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private ReimbursementResponse link(TransactionResponse income, TransactionResponse expense, long amountCents) {
        ResponseEntity<ReimbursementResponse> response = session.post(
                path("/transactions/" + income.id() + "/reimbursements"),
                new ReimbursementRequest(expense.id(), amountCents),
                ReimbursementResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String tryLink(TransactionResponse income, TransactionResponse expense, long amountCents) {
        return errorCode(session.post(
                path("/transactions/" + income.id() + "/reimbursements"),
                new ReimbursementRequest(expense.id(), amountCents),
                Map.class));
    }

    private String errorCode(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        return (String) response.getBody().get("code");
    }

    private long fixedSpent(String month) {
        return allocation(month).pillars().stream()
                .filter(p -> p.pillar() == Pillar.FIXED_COSTS)
                .findFirst()
                .orElseThrow()
                .spentCents();
    }

    private PillarAllocationReportResponse allocation(String month) {
        return session.get(path("/dashboard/pillar-allocation?month=" + month), PillarAllocationReportResponse.class)
                .getBody();
    }

    private DashboardSummaryResponse summary(String month) {
        return session.get(path("/dashboard/summary?month=" + month), DashboardSummaryResponse.class).getBody();
    }

    private CategoryResponse incomeCategory() {
        return Arrays.stream(session.get(path("/categories?type=INCOME"), CategoryResponse[].class).getBody())
                .filter(c -> !c.system())
                .findFirst()
                .orElseThrow();
    }

    private CategoryResponse expenseCategory(Pillar pillar) {
        return Arrays.stream(session.get(path("/categories?type=EXPENSE"), CategoryResponse[].class).getBody())
                .filter(c -> !c.system() && c.pillar() == pillar)
                .findFirst()
                .orElseThrow();
    }
}
