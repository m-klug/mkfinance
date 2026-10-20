package br.com.mkfinance.fixedincome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.fixedincome.FixedIncomeSimulationRequest;
import br.com.mkfinance.api.fixedincome.FixedIncomeSimulationRequest.Item;
import br.com.mkfinance.api.fixedincome.FixedIncomeSimulationResponse;
import br.com.mkfinance.application.marketrate.MarketRateQuote;
import br.com.mkfinance.application.marketrate.MarketRateSource;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.MarketRateCode;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import br.com.mkfinance.infrastructure.persistence.MarketRateRepository;
import br.com.mkfinance.support.ApiSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** POST /fixed-income/simulations through the real HTTP + security stack; the Banco Central is a fake. */
@TestPropertySource(properties = "mkfinance.market-rates.retry-after=PT0S")
class FixedIncomeSimulationIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate START = LocalDate.of(2027, 1, 4);

    @MockitoBean
    private MarketRateSource source;

    @Autowired
    private MarketRateRepository rates;

    private ApiSession session;

    @BeforeEach
    void signIn() {
        rates.deleteAll();
        reset(source);
        session = newSession();
        session.primeCsrf();
        session.post("/auth/register",
                new RegisterRequest("Tester", "sim+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                Object.class);
    }

    private void publishMarket() {
        when(source.latest(eq(MarketRateCode.CDI), any()))
                .thenReturn(Optional.of(new MarketRateQuote(LocalDate.of(2026, 10, 19), new BigDecimal("13.65"))));
        when(source.latest(eq(MarketRateCode.SELIC), any()))
                .thenReturn(Optional.of(new MarketRateQuote(LocalDate.of(2026, 10, 20), new BigDecimal("13.75"))));
        when(source.latest(eq(MarketRateCode.IPCA), any()))
                .thenReturn(Optional.of(new MarketRateQuote(LocalDate.of(2026, 9, 1), new BigDecimal("4.58"))));
    }

    private static Item prefixed(InstrumentType type, String rate, long initial, Long monthly, int months) {
        return new Item(type, Indexer.PREFIXED, new BigDecimal(rate), initial, monthly, months, START, null, null, null);
    }

    private static Item cdiPercent(InstrumentType type, String percent, BigDecimal cdi) {
        return new Item(type, Indexer.CDI_PERCENT, new BigDecimal(percent), 1_000_000L, null, 12, START, cdi, null, null);
    }

    private ResponseEntity<FixedIncomeSimulationResponse> simulate(Item... items) {
        return session.post("/fixed-income/simulations", new FixedIncomeSimulationRequest(List.of(items)),
                FixedIncomeSimulationResponse.class);
    }

    private ErrorResponse errorOf(Item... items) {
        ResponseEntity<ErrorResponse> response = session.post("/fixed-income/simulations",
                new FixedIncomeSimulationRequest(List.of(items)), ErrorResponse.class);
        assertThat(response.getStatusCode().isError()).isTrue();
        return response.getBody();
    }

    @Test
    @DisplayName("a prefixed LCI: gross equals net, series covers every month, no comparison")
    void singlePrefixedLci() {
        ResponseEntity<FixedIncomeSimulationResponse> response = simulate(prefixed(InstrumentType.LCI, "12", 1_000_000L, null, 12));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        FixedIncomeSimulationResponse body = response.getBody();
        assertThat(body.comparison()).isNull();
        FixedIncomeSimulationResponse.Result result = body.results().get(0);
        assertThat(result.investedCents()).isEqualTo(1_000_000L);
        assertThat(result.grossCents()).isBetween(1_115_000L, 1_125_000L);
        assertThat(result.netCents()).isEqualTo(result.grossCents());
        assertThat(result.incomeTaxCents()).isZero();
        assertThat(result.endDate()).isEqualTo(START.plusMonths(12));
        assertThat(result.series()).hasSize(13);
        assertThat(result.assumptions().cdiAnnualPercent()).isNull();
        verifyNoInteractions(source);
    }

    @Test
    @DisplayName("an omitted CDI is filled from the stored market rate and echoed back")
    void cdiFilledFromMarket() {
        publishMarket();

        FixedIncomeSimulationResponse.Result result = simulate(cdiPercent(InstrumentType.CDB, "110", null)).getBody().results().get(0);

        assertThat(result.assumptions().cdiAnnualPercent()).isEqualByComparingTo("13.65");
        assertThat(result.assumptions().selicAnnualPercent()).isNull();
        assertThat(result.grossYieldCents()).isBetween(140_000L, 170_000L);
    }

    @Test
    @DisplayName("a CDI given by the user is used as is and the provider is never asked")
    void cdiGivenByUser() {
        FixedIncomeSimulationResponse.Result result =
                simulate(cdiPercent(InstrumentType.CDB, "100", new BigDecimal("10"))).getBody().results().get(0);

        assertThat(result.assumptions().cdiAnnualPercent()).isEqualByComparingTo("10");
        assertThat(result.grossYieldCents()).isBetween(95_000L, 105_000L);
        verifyNoInteractions(source);
    }

    @Test
    @DisplayName("Tesouro Selic uses the stored Selic, Tesouro IPCA+ the stored IPCA suggestion")
    void selicAndIpcaFilledFromMarket() {
        publishMarket();

        FixedIncomeSimulationResponse body = simulate(
                new Item(InstrumentType.TESOURO_SELIC, Indexer.SELIC_PERCENT, new BigDecimal("100"), 1_000_000L, null, 12, START, null, null, null),
                new Item(InstrumentType.TESOURO_IPCA, Indexer.IPCA_PLUS, new BigDecimal("6"), 1_000_000L, null, 12, START, null, null, null)).getBody();

        assertThat(body.results().get(0).assumptions().selicAnnualPercent()).isEqualByComparingTo("13.75");
        assertThat(body.results().get(1).assumptions().ipcaAnnualPercent()).isEqualByComparingTo("4.58");
        assertThat(body.results().get(0).custodyCents()).isPositive();
    }

    @Test
    @DisplayName("two simulations are compared by net annual equivalent: CDB 120% of the CDI beats LCI 90%")
    void comparison() {
        BigDecimal cdi = new BigDecimal("14.90");

        FixedIncomeSimulationResponse body = simulate(
                cdiPercent(InstrumentType.LCI, "90", cdi), cdiPercent(InstrumentType.CDB, "120", cdi)).getBody();

        assertThat(body.results()).hasSize(2);
        assertThat(body.comparison().betterIndex()).isEqualTo(1);
        assertThat(body.comparison().netDifferenceCents())
                .isEqualTo(body.results().get(1).netCents() - body.results().get(0).netCents());
        assertThat(body.comparison().netAnnualEquivalentDifferencePercent()).isPositive();
    }

    @Test
    @DisplayName("identical simulations tie")
    void tie() {
        Item same = prefixed(InstrumentType.LCI, "12", 1_000_000L, null, 12);

        FixedIncomeSimulationResponse.Comparison comparison = simulate(same, same).getBody().comparison();

        assertThat(comparison.betterIndex()).isNull();
        assertThat(comparison.netDifferenceCents()).isZero();
    }

    @Test
    @DisplayName("an indexer the instrument is not sold with is refused, naming the simulation")
    void indexerNotAvailable() {
        ErrorResponse error = errorOf(
                prefixed(InstrumentType.CDB, "12", 1_000_000L, null, 12),
                new Item(InstrumentType.CDB, Indexer.SELIC_PERCENT, new BigDecimal("100"), 1_000_000L, null, 12, START, null, null, null));

        assertThat(error.code()).isEqualTo(ErrorCode.SIMULATION_INDEXER_NOT_AVAILABLE);
        assertThat(error.params()).containsEntry("index", 1).containsEntry("indexer", "SELIC_PERCENT");
    }

    @Test
    @DisplayName("nothing to apply is refused")
    void nothingApplied() {
        ErrorResponse error = errorOf(prefixed(InstrumentType.CDB, "12", 0, 100_000L, 1));

        assertThat(error.code()).isEqualTo(ErrorCode.SIMULATION_NOTHING_APPLIED);
    }

    @Test
    @DisplayName("an assumption missing with the Banco Central unreachable and nothing stored answers 503")
    void marketRateUnavailable() {
        when(source.latest(any(), any())).thenThrow(new IllegalStateException("BCB fora do ar"));

        ResponseEntity<ErrorResponse> response = session.post("/fixed-income/simulations",
                new FixedIncomeSimulationRequest(List.of(cdiPercent(InstrumentType.CDB, "100", null))), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.MARKET_RATE_UNAVAILABLE);
        assertThat(response.getBody().params()).containsEntry("rate", "CDI");
    }

    @Test
    @DisplayName("bean validation: zero or three simulations, term out of range, negative rate")
    void validation() {
        assertThat(errorOf().code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        Item ok = prefixed(InstrumentType.LCI, "12", 1_000_000L, null, 12);
        assertThat(errorOf(ok, ok, ok).code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(errorOf(prefixed(InstrumentType.LCI, "12", 1_000_000L, null, 0)).code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(errorOf(prefixed(InstrumentType.LCI, "12", 1_000_000L, null, 601)).code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(errorOf(prefixed(InstrumentType.LCI, "-1", 1_000_000L, null, 12)).code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("requires a signed-in user")
    void requiresAuthentication() {
        ApiSession anonymous = newSession();
        anonymous.primeCsrf();

        ResponseEntity<String> response = anonymous.post("/fixed-income/simulations",
                new FixedIncomeSimulationRequest(List.of(prefixed(InstrumentType.LCI, "12", 1_000_000L, null, 12))), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
