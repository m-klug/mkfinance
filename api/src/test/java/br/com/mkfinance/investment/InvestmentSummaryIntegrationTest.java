package br.com.mkfinance.investment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.card.CardPurchaseRequest;
import br.com.mkfinance.api.card.CardRequest;
import br.com.mkfinance.api.card.CardResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.investment.InvestmentSummaryResponse;
import br.com.mkfinance.api.investment.PositionResponse;
import br.com.mkfinance.api.investment.PositionSettleRequest;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import br.com.mkfinance.support.ApiSession;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Net worth (DESENVOLVIMENTO-POSICOES.md §7): the balances of every account plus the yield the active
 * positions have accrued and not yet received, through the real HTTP stack against a real Postgres.
 */
class InvestmentSummaryIntegrationTest extends AbstractPositionIntegrationTest {

    private InvestmentSummaryResponse summary() {
        ResponseEntity<InvestmentSummaryResponse> response =
                session.get(path("/investments/summary"), InvestmentSummaryResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void setInitialBalance(AccountResponse account, long cents) {
        session.patch(path("/accounts/" + account.id()),
                new AccountRequest(account.name(), account.type(), cents, null, null, null), AccountResponse.class);
    }

    private PositionResponse[] activePositions() {
        return session.get(path("/investments/positions?status=ACTIVE"), PositionResponse[].class).getBody();
    }

    private long estimatedValueOf(PositionResponse[] positions) {
        return Arrays.stream(positions).mapToLong(p -> p.estimate().today().netCents()).sum();
    }

    @Test
    @DisplayName("an account with no positions is just its balance")
    void noPositions() {
        InvestmentSummaryResponse summary = summary();

        assertThat(summary.accountsAndCashCents()).isEqualTo(1_000_000L); // checking
        assertThat(summary.investmentsCents()).isZero();
        assertThat(summary.estimatedNetWorthCents()).isEqualTo(1_000_000L);
        assertThat(summary.assetClasses()).hasSize(1);
        assertThat(summary.assetClasses().get(0).code()).isEqualTo("FIXED_INCOME");
        assertThat(summary.assetClasses().get(0).positions()).isZero();
        assertThat(summary.assetClasses().get(0).accruedYieldCents()).isZero();
        assertThat(summary.positionsExceedBalance()).isFalse();
    }

    @Test
    @DisplayName("net worth = accounts and cash + investment balances + the yield positions have accrued")
    void netWorthAddsTheAccruedYield() {
        setInitialBalance(investment, 300_000L); // a title bought before the app, already in the balance
        UUID leg = contribute(500_000L, today.minusDays(100), null);
        create(linked(leg, InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(265)));
        create(manual(investment.id(), 300_000L, today.minusDays(150), null,
                InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(100)));
        createAccount("Carteira", AccountType.CASH, 20_000L);
        createAccount("Poupança", AccountType.SAVINGS, 50_000L);

        InvestmentSummaryResponse summary = summary();
        long accrued = estimatedValueOf(activePositions()) - 800_000L;

        assertThat(accrued).isPositive();
        assertThat(summary.accountsAndCashCents()).isEqualTo(500_000L + 20_000L); // checking after the purchase + cash
        assertThat(summary.investmentsCents()).isEqualTo(800_000L + 50_000L + accrued);
        assertThat(summary.estimatedNetWorthCents()).isEqualTo(summary.accountsAndCashCents() + summary.investmentsCents());
        assertThat(summary.assetClasses().get(0).appliedCents()).isEqualTo(800_000L);
        assertThat(summary.assetClasses().get(0).accruedYieldCents()).isEqualTo(accrued);
        assertThat(summary.assetClasses().get(0).positions()).isEqualTo(2);
        assertThat(summary.assetClasses().get(0).positionsWithoutEstimate()).isZero();
    }

    @Test
    @DisplayName("savings count with investments, an archived account is left out")
    void accountGrouping() {
        createAccount("Poupança", AccountType.SAVINGS, 50_000L);
        AccountResponse old = createAccount("Conta antiga", AccountType.CHECKING, 700_000L);
        session.post(path("/accounts/" + old.id() + "/archive"), null, AccountResponse.class);

        InvestmentSummaryResponse summary = summary();

        assertThat(summary.accountsAndCashCents()).isEqualTo(1_000_000L); // the archived 700k is not here
        assertThat(summary.investmentsCents()).isEqualTo(50_000L);
        assertThat(summary.accounts()).extracting(InvestmentSummaryResponse.InvestmentAccount::name)
                .containsExactlyInAnyOrder("Corretora", "Poupança");
    }

    @Test
    @DisplayName("what no position explains is shown as unregistered, per account")
    void unregistered() {
        setInitialBalance(investment, 300_000L);
        UUID leg = contribute(500_000L, today.minusDays(100), null);
        create(linked(leg, InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(265)));

        InvestmentSummaryResponse summary = summary();

        InvestmentSummaryResponse.InvestmentAccount account = summary.accounts().get(0);
        assertThat(account.balanceCents()).isEqualTo(800_000L);
        assertThat(account.positionsPrincipalCents()).isEqualTo(500_000L);
        assertThat(account.unregisteredCents()).isEqualTo(300_000L);
        assertThat(summary.unregisteredInvestmentCents()).isEqualTo(300_000L);
        assertThat(summary.positionsExceedBalance()).isFalse();
    }

    @Test
    @DisplayName("positions claiming more than the account holds raise the warning, naming the account")
    void positionsExceedTheBalance() {
        // Titles bought before the app, but the account's initial balance was never raised to include them.
        create(manual(investment.id(), 400_000L, today.minusDays(150), null,
                InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(100)));

        InvestmentSummaryResponse summary = summary();

        assertThat(summary.positionsExceedBalance()).isTrue();
        InvestmentSummaryResponse.InvestmentAccount account = summary.accounts().get(0);
        assertThat(account.positionsExceedBalance()).isTrue();
        assertThat(account.balanceCents()).isZero();
        assertThat(account.positionsPrincipalCents()).isEqualTo(400_000L);
        assertThat(account.unregisteredCents()).isZero(); // never negative
    }

    @Test
    @DisplayName("a paid position leaves the totals of the active ones: its yield is already in the balance")
    void paidPositionIsNotCountedAgain() {
        setInitialBalance(investment, 300_000L);
        UUID leg = contribute(500_000L, today.minusDays(100), null);
        create(linked(leg, InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(265)));
        PositionResponse legacy = create(manual(investment.id(), 300_000L, today.minusDays(150), null,
                InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(100)));
        session.post(path("/investments/positions/" + legacy.id() + "/settle"),
                new PositionSettleRequest(today, 310_000L, null), PositionResponse.class);

        InvestmentSummaryResponse summary = summary();
        long accruedOfTheActiveOne = estimatedValueOf(activePositions()) - 500_000L;

        assertThat(summary.assetClasses().get(0).positions()).isEqualTo(1);
        assertThat(summary.assetClasses().get(0).appliedCents()).isEqualTo(500_000L);
        assertThat(summary.assetClasses().get(0).accruedYieldCents()).isEqualTo(accruedOfTheActiveOne);
        assertThat(summary.accounts().get(0).balanceCents()).isEqualTo(810_000L); // the booked 10k yield is in it
        assertThat(summary.accounts().get(0).unregisteredCents()).isEqualTo(310_000L);
    }

    @Test
    @DisplayName("card bills still owed are shown beside the net worth and not subtracted from it")
    void openStatementsAreInformational() {
        long before = summary().estimatedNetWorthCents();
        CardResponse card = session.post(path("/cards"),
                new CardRequest("Nubank", "VISA", 5_000_00L, (short) 28, (short) 5, checking.id(), null),
                CardResponse.class).getBody();
        CategoryResponse category = Arrays.stream(session.get(path("/categories?type=EXPENSE"), CategoryResponse[].class).getBody())
                .filter(c -> !c.system() && c.pillar() == Pillar.FIXED_COSTS).findFirst().orElseThrow();
        session.post(path("/cards/" + card.id() + "/purchases"),
                new CardPurchaseRequest(category.id(), "compra", 150_000L, LocalDate.of(2026, 3, 5), (short) 1),
                Object.class);
        session.get(path("/cards/" + card.id() + "/statements?from=2026-03&to=2026-03"), Object[].class); // materializes the bill

        InvestmentSummaryResponse summary = summary();

        assertThat(summary.openStatementsCents()).isEqualTo(150_000L);
        assertThat(summary.estimatedNetWorthCents()).isEqualTo(before);
    }

    @Test
    @DisplayName("another workspace sees only its own money")
    void isolatedPerWorkspace() {
        UUID leg = contribute(500_000L, today.minusDays(100), null);
        create(linked(leg, InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(265)));
        ApiSession other = newSession();
        other.primeCsrf();
        CurrentUserResponse user = other.post("/auth/register",
                new RegisterRequest("Outro", "sum2+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                CurrentUserResponse.class).getBody();

        InvestmentSummaryResponse theirs = other.get(
                "/workspaces/" + user.workspaces().get(0).id() + "/investments/summary",
                InvestmentSummaryResponse.class).getBody();

        assertThat(theirs.estimatedNetWorthCents()).isZero();
        assertThat(theirs.assetClasses().get(0).positions()).isZero();
        assertThat(theirs.accounts()).isEmpty();
    }
}
