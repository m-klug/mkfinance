package br.com.mkfinance.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.transaction.TransactionPageResponse;
import br.com.mkfinance.api.transaction.TransactionRequest;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.api.transaction.TransferRequest;
import br.com.mkfinance.application.transaction.AccountBalanceService;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * PLANEJAMENTO §5 R4 and R9 — a transfer is a mirrored pair that is created, edited and deleted
 * atomically on both sides, is never income nor expense, and becomes a contribution ("aporte")
 * when its destination is an INVESTMENT/SAVINGS account.
 */
class TransferIntegrationTest extends AbstractTransactionIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    @Autowired
    private AccountBalanceService balanceService;

    private AccountResponse checking;
    private AccountResponse wallet;

    @BeforeEach
    void setUp() {
        register();
        checking = createAccount("Conta Corrente", AccountType.CHECKING, 500_000L);
        wallet = createAccount("Carteira", AccountType.CASH, 0L);
    }

    private TransactionResponse[] allTransactions() {
        return session.get(path("/transactions?size=200"), TransactionPageResponse.class)
                .getBody()
                .content()
                .toArray(new TransactionResponse[0]);
    }

    private TransactionResponse legOn(UUID accountId) {
        return Arrays.stream(allTransactions())
                .filter(transaction -> transaction.accountId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No transaction on account " + accountId));
    }

    @Test
    @DisplayName("creating a transfer writes both legs and moves the money between accounts")
    void createTransferCreatesMirroredPair() {
        ResponseEntity<TransactionResponse> created = session.post(
                path("/transfers"),
                new TransferRequest(checking.id(), wallet.id(), 120_000L, DATE, "Saque", null),
                TransactionResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransactionResponse outgoing = created.getBody();
        assertThat(outgoing.accountId()).isEqualTo(checking.id());
        assertThat(outgoing.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(outgoing.categoryId()).as("transfer legs carry no category").isNull();
        assertThat(outgoing.transferPairId()).isNotNull();
        assertThat(outgoing.contributionPillar()).as("plain transfer, not a contribution").isNull();

        TransactionResponse incoming = legOn(wallet.id());
        assertThat(incoming.id()).isEqualTo(outgoing.transferPairId());
        assertThat(incoming.transferPairId()).as("the legs point at each other").isEqualTo(outgoing.id());
        assertThat(incoming.amountCents()).isEqualTo(outgoing.amountCents());
        assertThat(incoming.date()).isEqualTo(outgoing.date());
        assertThat(incoming.description()).isEqualTo(outgoing.description());

        // R4/R5: money moved, the workspace total is unchanged.
        assertThat(balanceService.balanceOf(workspaceId, checking.id()).currentCents()).isEqualTo(380_000L);
        assertThat(balanceService.balanceOf(workspaceId, wallet.id()).currentCents()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("editing one leg edits its pair atomically")
    void editingOneLegUpdatesBothSides() {
        TransactionResponse outgoing = session.post(
                        path("/transfers"),
                        new TransferRequest(checking.id(), wallet.id(), 100_000L, DATE, "Saque", null),
                        TransactionResponse.class)
                .getBody();

        LocalDate newDate = DATE.plusDays(3);
        ResponseEntity<TransactionResponse> updated = session.patch(
                path("/transactions/" + outgoing.id()),
                new TransactionRequest(
                        checking.id(), null, null, 75_000L, newDate, "Saque corrigido", "conferido", null),
                TransactionResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        for (TransactionResponse leg : allTransactions()) {
            assertThat(leg.amountCents()).isEqualTo(75_000L);
            assertThat(leg.date()).isEqualTo(newDate);
            assertThat(leg.description()).isEqualTo("Saque corrigido");
            assertThat(leg.note()).isEqualTo("conferido");
        }
        assertThat(balanceService.balanceOf(workspaceId, checking.id()).currentCents()).isEqualTo(425_000L);
        assertThat(balanceService.balanceOf(workspaceId, wallet.id()).currentCents()).isEqualTo(75_000L);
    }

    @Test
    @DisplayName("editing a leg's account moves only that side of the transfer")
    void editingLegAccountMovesOnlyThatSide() {
        AccountResponse savings = createAccount("Poupança", AccountType.SAVINGS, 0L, ContributionPillar.GOALS);
        TransactionResponse outgoing = session.post(
                        path("/transfers"),
                        new TransferRequest(checking.id(), wallet.id(), 50_000L, DATE, "Saque", null),
                        TransactionResponse.class)
                .getBody();

        // Redirect the incoming leg to the savings account: it becomes a contribution (R9).
        TransactionResponse incoming = legOn(wallet.id());
        session.patch(
                path("/transactions/" + incoming.id()),
                new TransactionRequest(savings.id(), null, null, 50_000L, DATE, "Aporte", null, null),
                TransactionResponse.class);

        TransactionResponse movedLeg = legOn(savings.id());
        assertThat(movedLeg.id()).isEqualTo(incoming.id());
        assertThat(movedLeg.contributionPillar())
                .as("destination now defaults the pillar")
                .isEqualTo(ContributionPillar.GOALS);
        assertThat(legOn(checking.id()).id()).as("the other leg stayed put").isEqualTo(outgoing.id());

        assertThat(balanceService.balanceOf(workspaceId, wallet.id()).currentCents()).isZero();
        assertThat(balanceService.balanceOf(workspaceId, savings.id()).currentCents()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("deleting one leg deletes its pair atomically")
    void deletingOneLegDeletesBothSides() {
        TransactionResponse outgoing = session.post(
                        path("/transfers"),
                        new TransferRequest(checking.id(), wallet.id(), 90_000L, DATE, "Saque", null),
                        TransactionResponse.class)
                .getBody();
        assertThat(allTransactions()).hasSize(2);

        ResponseEntity<Void> deleted = session.delete(path("/transactions/" + outgoing.id()), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(allTransactions()).as("no half-transfer left behind").isEmpty();
        assertThat(balanceService.balanceOf(workspaceId, checking.id()).currentCents()).isEqualTo(500_000L);
        assertThat(balanceService.balanceOf(workspaceId, wallet.id()).currentCents()).isZero();
    }

    @Test
    @DisplayName("settling one leg settles its pair")
    void settlingOneLegSettlesBothSides() {
        TransactionResponse outgoing = session.post(
                        path("/transfers"),
                        new TransferRequest(checking.id(), wallet.id(), 30_000L, DATE, "Saque", null),
                        TransactionResponse.class)
                .getBody();
        session.patch(
                path("/transactions/" + outgoing.id()),
                new TransactionRequest(
                        checking.id(), null, null, 30_000L, DATE, "Saque", null, TransactionStatus.PENDING),
                TransactionResponse.class);
        assertThat(allTransactions()).allSatisfy(leg -> assertThat(leg.status()).isEqualTo(TransactionStatus.PENDING));
        assertThat(balanceService.balanceOf(workspaceId, checking.id()).currentCents())
                .as("pending money has not left yet")
                .isEqualTo(500_000L);

        session.post(path("/transactions/" + outgoing.id() + "/settle"), null, TransactionResponse.class);

        assertThat(allTransactions()).allSatisfy(leg -> assertThat(leg.status()).isEqualTo(TransactionStatus.SETTLED));
        assertThat(balanceService.balanceOf(workspaceId, checking.id()).currentCents()).isEqualTo(470_000L);
        assertThat(balanceService.balanceOf(workspaceId, wallet.id()).currentCents()).isEqualTo(30_000L);
    }

    @Test
    @DisplayName("a transfer to the same account is refused")
    void transferToSameAccountIsRejected() {
        ResponseEntity<ErrorResponse> response = session.post(
                path("/transfers"),
                new TransferRequest(checking.id(), checking.id(), 10_000L, DATE, "Loop", null),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INSUFFICIENT_TRANSFER_ACCOUNTS");
    }

    @Test
    @DisplayName("another workspace's account is not reachable")
    void transferToForeignAccountIsNotFound() {
        UUID foreignAccountId = checking.id();
        UUID myWorkspace = workspaceId;
        register(); // a brand new user, hence a brand new workspace
        AccountResponse mine = createAccount("Minha Conta", AccountType.CHECKING, 1_000L);
        assertThat(workspaceId).isNotEqualTo(myWorkspace);

        ResponseEntity<ErrorResponse> response = session.post(
                path("/transfers"),
                new TransferRequest(mine.id(), foreignAccountId, 100L, DATE, "Invasão", null),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    @DisplayName("a transfer never shows up as income or expense (R4)")
    void transferIsNeitherIncomeNorExpense() {
        session.post(
                path("/transfers"),
                new TransferRequest(checking.id(), wallet.id(), 60_000L, DATE, "Saque", null),
                TransactionResponse.class);

        TransactionPageResponse expenses = session.get(
                        path("/transactions?type=EXPENSE"), TransactionPageResponse.class)
                .getBody();
        TransactionPageResponse incomes = session.get(path("/transactions?type=INCOME"), TransactionPageResponse.class)
                .getBody();

        assertThat(expenses.totalElements()).isZero();
        assertThat(incomes.totalElements()).isZero();
        assertThat(session.get(path("/transactions?type=TRANSFER"), TransactionPageResponse.class)
                        .getBody()
                        .totalElements())
                .isEqualTo(2L);
    }

    // --- Contribution / aporte (R9) ------------------------------------------------------------

    @Test
    @DisplayName("a transfer into an investment account defaults to that account's pillar")
    void contributionDefaultsToAccountPillar() {
        AccountResponse brokerage =
                createAccount("Corretora", AccountType.INVESTMENT, 0L, ContributionPillar.FINANCIAL_FREEDOM);

        TransactionResponse outgoing = session.post(
                        path("/transfers"),
                        new TransferRequest(checking.id(), brokerage.id(), 200_000L, DATE, "Aporte", null),
                        TransactionResponse.class)
                .getBody();

        assertThat(outgoing.contributionPillar())
                .as("the response echoes the pillar actually applied")
                .isEqualTo(ContributionPillar.FINANCIAL_FREEDOM);
        assertThat(legOn(brokerage.id()).contributionPillar()).isEqualTo(ContributionPillar.FINANCIAL_FREEDOM);
        assertThat(legOn(checking.id()).contributionPillar())
                .as("only the incoming leg carries the pillar, so it cannot be double-counted")
                .isNull();
    }

    @Test
    @DisplayName("the pillar can be overridden per contribution")
    void contributionPillarCanBeOverridden() {
        AccountResponse brokerage =
                createAccount("Corretora", AccountType.INVESTMENT, 0L, ContributionPillar.FINANCIAL_FREEDOM);

        TransactionResponse outgoing = session.post(
                        path("/transfers"),
                        new TransferRequest(
                                checking.id(), brokerage.id(), 80_000L, DATE, "Aporte", ContributionPillar.GOALS),
                        TransactionResponse.class)
                .getBody();

        assertThat(outgoing.contributionPillar()).isEqualTo(ContributionPillar.GOALS);
        assertThat(legOn(brokerage.id()).contributionPillar()).isEqualTo(ContributionPillar.GOALS);
    }

    @Test
    @DisplayName("an investment account with no default pillar demands one")
    void contributionWithoutAnyPillarIsRejected() {
        AccountResponse brokerage = createAccount("Corretora Sem Pilar", AccountType.INVESTMENT, 0L);

        ResponseEntity<ErrorResponse> response = session.post(
                path("/transfers"),
                new TransferRequest(checking.id(), brokerage.id(), 50_000L, DATE, "Aporte", null),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("CONTRIBUTION_PILLAR_REQUIRED");
    }

    @Test
    @DisplayName("a pillar on a non-investment destination is refused")
    void contributionPillarOnPlainAccountIsRejected() {
        ResponseEntity<ErrorResponse> response = session.post(
                path("/transfers"),
                new TransferRequest(
                        checking.id(), wallet.id(), 10_000L, DATE, "Saque", ContributionPillar.GOALS),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CONTRIBUTION_PILLAR");
    }

    @Test
    @DisplayName("a contribution is not an expense, it only moves money (R9)")
    void contributionIsNotAnExpense() {
        AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L, ContributionPillar.GOALS);
        session.post(
                path("/transfers"),
                new TransferRequest(checking.id(), brokerage.id(), 150_000L, DATE, "Aporte", null),
                TransactionResponse.class);

        assertThat(session.get(path("/transactions?type=EXPENSE"), TransactionPageResponse.class)
                        .getBody()
                        .totalElements())
                .isZero();

        Balance checkingBalance = balanceService.balanceOf(workspaceId, checking.id());
        Balance brokerageBalance = balanceService.balanceOf(workspaceId, brokerage.id());
        assertThat(checkingBalance.currentCents()).isEqualTo(350_000L);
        assertThat(brokerageBalance.currentCents()).isEqualTo(150_000L);
    }

    @Test
    @DisplayName("an explicitly chosen pillar survives a move to another investment account")
    void explicitPillarIsNotOverwrittenByTheNewDestinationDefault() {
        AccountResponse first = createAccount("Corretora A", AccountType.INVESTMENT, 0L, ContributionPillar.GOALS);
        AccountResponse second =
                createAccount("Corretora B", AccountType.INVESTMENT, 0L, ContributionPillar.FINANCIAL_FREEDOM);
        session.post(
                path("/transfers"),
                new TransferRequest(checking.id(), first.id(), 70_000L, DATE, "Aporte", ContributionPillar.GOALS),
                TransactionResponse.class);

        TransactionResponse incoming = legOn(first.id());
        session.patch(
                path("/transactions/" + incoming.id()),
                new TransactionRequest(second.id(), null, null, 70_000L, DATE, "Aporte", null, null),
                TransactionResponse.class);

        assertThat(legOn(second.id()).contributionPillar())
                .as("the user's explicit GOALS choice is kept, not replaced by B's FINANCIAL_FREEDOM default")
                .isEqualTo(ContributionPillar.GOALS);
    }

    @Test
    @DisplayName("moving a contribution to a plain account drops the pillar instead of failing")
    void movingContributionToPlainAccountClearsPillar() {
        AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L, ContributionPillar.GOALS);
        session.post(
                path("/transfers"),
                new TransferRequest(checking.id(), brokerage.id(), 40_000L, DATE, "Aporte", null),
                TransactionResponse.class);
        TransactionResponse incoming = legOn(brokerage.id());
        assertThat(incoming.contributionPillar()).isEqualTo(ContributionPillar.GOALS);

        ResponseEntity<TransactionResponse> moved = session.patch(
                path("/transactions/" + incoming.id()),
                new TransactionRequest(wallet.id(), null, null, 40_000L, DATE, "Saque afinal", null, null),
                TransactionResponse.class);

        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(legOn(wallet.id()).contributionPillar()).isNull();
    }
}
