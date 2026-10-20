package br.com.mkfinance.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.investment.PositionRequest;
import br.com.mkfinance.api.investment.PositionResponse;
import br.com.mkfinance.api.investment.PositionUpdateRequest;
import br.com.mkfinance.api.investment.UnlinkedContributionResponse;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.api.transaction.TransferRequest;
import br.com.mkfinance.api.transaction.TransferUpdateRequest;
import br.com.mkfinance.api.vault.VaultRequest;
import br.com.mkfinance.api.vault.VaultResponse;
import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.marketrate.IndexDailyQuote;
import br.com.mkfinance.application.marketrate.IndexHistorySource;
import br.com.mkfinance.application.marketrate.MarketRateQuote;
import br.com.mkfinance.application.marketrate.MarketRateSource;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.MarketRateCode;
import br.com.mkfinance.domain.model.PositionStatus;
import br.com.mkfinance.domain.service.fixedincome.BrazilianBusinessDays;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import br.com.mkfinance.support.ApiSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Shared fixture of the fixed-income position tests: a registered user with a checking and an
 * investment account, the fake Banco Central, and the helpers to buy, register and read titles.
 *
 * <p>Originally written for the positions end to end (DESENVOLVIMENTO-POSICOES.md): both origins, the estimates, the
 * rules and the guards on the purchase transfer, through the real HTTP + security stack against a real
 * Postgres. The Banco Central is replaced by a fake that publishes a flat CDI.
 */
abstract class AbstractPositionIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    protected MarketRateSource marketSource;

    @MockitoBean
    protected IndexHistorySource historySource;

    @Autowired
    protected BusinessClock clock;

    /** The outgoing (checking side) leg of the last purchase made with {@link #contribute}. */
    protected UUID lastOutgoingLegId;
    protected ApiSession session;
    protected UUID workspaceId;
    protected LocalDate today;
    protected AccountResponse checking;
    protected AccountResponse investment;

    @BeforeEach
    void setUp() {
        reset(marketSource, historySource);
        today = clock.today();
        when(marketSource.latest(eq(MarketRateCode.CDI), any()))
                .thenReturn(Optional.of(new MarketRateQuote(today.minusDays(1), new BigDecimal("13.65"))));
        when(marketSource.latest(eq(MarketRateCode.SELIC), any()))
                .thenReturn(Optional.of(new MarketRateQuote(today, new BigDecimal("13.75"))));
        when(marketSource.latest(eq(MarketRateCode.IPCA), any()))
                .thenReturn(Optional.of(new MarketRateQuote(today.minusMonths(1), new BigDecimal("4.58"))));
        doAnswer(invocation -> flatHistory(invocation.getArgument(1), invocation.getArgument(2)))
                .when(historySource).between(any(), any(), any());

        session = newSession();
        session.primeCsrf();
        CurrentUserResponse user = session.post(
                        "/auth/register",
                        new RegisterRequest("Tester", "pos+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                        CurrentUserResponse.class)
                .getBody();
        workspaceId = user.workspaces().get(0).id();
        checking = createAccount("Conta corrente", AccountType.CHECKING, 1_000_000L);
        investment = createAccount("Corretora", AccountType.INVESTMENT, 0L);
    }

    protected static List<IndexDailyQuote> flatHistory(LocalDate from, LocalDate to) {
        List<IndexDailyQuote> quotes = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (BrazilianBusinessDays.isBusinessDay(day)) {
                quotes.add(new IndexDailyQuote(day, new BigDecimal("0.050788")));
            }
        }
        return quotes;
    }

    protected String path(String suffix) {
        return "/workspaces/" + workspaceId + suffix;
    }

    protected AccountResponse createAccount(String name, AccountType type, long initial) {
        return session.post(path("/accounts"), new AccountRequest(name, type, initial, null, null, null), AccountResponse.class)
                .getBody();
    }

    protected AccountResponse account(UUID id) {
        return java.util.Arrays.stream(session.get(path("/accounts"), AccountResponse[].class).getBody())
                .filter(a -> a.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    protected VaultResponse createVault(UUID accountId, String name) {
        return session.post(path("/accounts/" + accountId + "/vaults"),
                new VaultRequest(name, ContributionPillar.FINANCIAL_FREEDOM, null, null, null), VaultResponse.class).getBody();
    }

    /** A transfer checking -> investment; returns the id of the leg that entered the investment account. */
    protected UUID contribute(long cents, LocalDate date, UUID vaultId) {
        ResponseEntity<TransactionResponse> created = session.post(path("/transfers"),
                new TransferRequest(checking.id(), investment.id(), cents, date, "Compra CDB",
                        vaultId == null ? ContributionPillar.GOALS : null, null, vaultId), TransactionResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        lastOutgoingLegId = created.getBody().id();
        return unlinked().stream()
                .filter(c -> c.amountCents() == cents && c.date().equals(date))
                .findFirst().orElseThrow().transactionId();
    }

    protected List<UnlinkedContributionResponse> unlinked() {
        return List.of(session.get(path("/investments/unlinked-contributions"), UnlinkedContributionResponse[].class).getBody());
    }

    protected PositionRequest linked(UUID contributionId, InstrumentType type, Indexer indexer, String rate, LocalDate maturity) {
        return new PositionRequest(contributionId, null, null, null, null, "CDB teste", type, indexer, new BigDecimal(rate), maturity);
    }

    protected PositionRequest manual(UUID accountId, long principal, LocalDate purchase, UUID vaultId,
                                   InstrumentType type, Indexer indexer, String rate, LocalDate maturity) {
        return new PositionRequest(null, accountId, principal, purchase, vaultId, "Antigo", type, indexer, new BigDecimal(rate), maturity);
    }

    protected PositionResponse create(PositionRequest request) {
        ResponseEntity<PositionResponse> response = session.post(path("/investments/positions"), request, PositionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    protected ErrorResponse createFails(PositionRequest request) {
        ResponseEntity<ErrorResponse> response = session.post(path("/investments/positions"), request, ErrorResponse.class);
        assertThat(response.getStatusCode().isError()).isTrue();
        return response.getBody();
    }
}
