package br.com.mkfinance.marketrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.marketrate.MarketRateResponse;
import br.com.mkfinance.application.marketrate.MarketRateQuote;
import br.com.mkfinance.application.marketrate.MarketRateSource;
import br.com.mkfinance.domain.model.MarketRate;
import br.com.mkfinance.domain.model.MarketRateCode;
import br.com.mkfinance.infrastructure.persistence.MarketRateRepository;
import br.com.mkfinance.support.ApiSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

/**
 * GET /market-rates against a real Postgres, with the Banco Central replaced by a fake. The retry
 * pause after a failure is switched off so each test sees its own provider behaviour.
 */
@TestPropertySource(properties = "mkfinance.market-rates.retry-after=PT0S")
class MarketRateIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private MarketRateSource source;

    @Autowired
    private MarketRateRepository repository;

    private ApiSession session;

    @BeforeEach
    void signIn() {
        repository.deleteAll();
        reset(source);
        session = newSession();
        session.primeCsrf();
        session.post("/auth/register",
                new RegisterRequest("Tester", "rates+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                Object.class);
    }

    private void publish(MarketRateCode code, LocalDate date, String value) {
        when(source.latest(eq(code), any())).thenReturn(Optional.of(new MarketRateQuote(date, new BigDecimal(value))));
    }

    private MarketRateResponse[] read() {
        ResponseEntity<MarketRateResponse[]> response = session.get("/market-rates", MarketRateResponse[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    @DisplayName("a first read fetches CDI, Selic and IPCA, stores them and answers in that order")
    void firstReadFetchesAndStores() {
        publish(MarketRateCode.CDI, LocalDate.of(2026, 10, 19), "13.65");
        publish(MarketRateCode.SELIC, LocalDate.of(2026, 10, 20), "13.75");
        publish(MarketRateCode.IPCA, LocalDate.of(2026, 9, 1), "4.58");

        MarketRateResponse[] rates = read();

        assertThat(rates).extracting(MarketRateResponse::code).containsExactly("CDI", "SELIC", "IPCA");
        assertThat(rates[0].ratePercent()).isEqualByComparingTo("13.65");
        assertThat(rates[0].referenceDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(rates[2].ratePercent()).isEqualByComparingTo("4.58");
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("a second read within the refresh interval does not call the provider again")
    void freshValueIsServedFromDatabase() {
        publish(MarketRateCode.CDI, LocalDate.of(2026, 10, 19), "13.65");
        publish(MarketRateCode.SELIC, LocalDate.of(2026, 10, 20), "13.75");
        publish(MarketRateCode.IPCA, LocalDate.of(2026, 9, 1), "4.58");

        read();
        read();

        verify(source, times(3)).latest(any(), any());
    }

    @Test
    @DisplayName("a stale value is refreshed and the newer reference date becomes the current one")
    void staleValueIsRefreshed() {
        publish(MarketRateCode.CDI, LocalDate.of(2026, 10, 19), "13.65");
        publish(MarketRateCode.SELIC, LocalDate.of(2026, 10, 20), "13.75");
        publish(MarketRateCode.IPCA, LocalDate.of(2026, 9, 1), "4.58");
        read();
        repository.findAll().forEach(row -> {
            row.setFetchedAt(OffsetDateTime.now().minusDays(1));
            repository.save(row);
        });
        publish(MarketRateCode.CDI, LocalDate.of(2026, 10, 20), "13.90");

        MarketRateResponse cdi = read()[0];

        assertThat(cdi.ratePercent()).isEqualByComparingTo("13.90");
        assertThat(cdi.referenceDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(repository.findAll().stream().filter(r -> r.getCode() == MarketRateCode.CDI)).hasSize(2);
    }

    @Test
    @DisplayName("refetching the same reference date updates the row instead of duplicating it")
    void sameDateUpdatesRow() {
        publish(MarketRateCode.CDI, LocalDate.of(2026, 10, 19), "13.65");
        publish(MarketRateCode.SELIC, LocalDate.of(2026, 10, 20), "13.75");
        publish(MarketRateCode.IPCA, LocalDate.of(2026, 9, 1), "4.58");
        read();
        repository.findAll().forEach(row -> {
            row.setFetchedAt(OffsetDateTime.now().minusDays(1));
            repository.save(row);
        });
        publish(MarketRateCode.CDI, LocalDate.of(2026, 10, 19), "13.66");

        MarketRateResponse cdi = read()[0];

        assertThat(cdi.ratePercent()).isEqualByComparingTo("13.66");
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("when the provider is down the last stored values are still served")
    void providerDownServesLastStored() {
        repository.save(MarketRate.builder().code(MarketRateCode.CDI).referenceDate(LocalDate.of(2026, 10, 1))
                .ratePercent(new BigDecimal("13.65")).fetchedAt(OffsetDateTime.now().minusDays(3)).build());
        when(source.latest(any(), any())).thenThrow(new IllegalStateException("BCB fora do ar"));

        MarketRateResponse[] rates = read();

        assertThat(rates).hasSize(1);
        assertThat(rates[0].code()).isEqualTo("CDI");
        assertThat(rates[0].ratePercent()).isEqualByComparingTo("13.65");
    }

    @Test
    @DisplayName("when the provider is down and nothing was ever stored the answer is empty, not an error")
    void providerDownAndEmpty() {
        when(source.latest(any(), any())).thenThrow(new IllegalStateException("BCB fora do ar"));

        assertThat(read()).isEmpty();
    }

    @Test
    @DisplayName("one rate failing does not hide the others")
    void partialFailure() {
        publish(MarketRateCode.CDI, LocalDate.of(2026, 10, 19), "13.65");
        when(source.latest(eq(MarketRateCode.SELIC), any())).thenThrow(new IllegalStateException("x"));
        publish(MarketRateCode.IPCA, LocalDate.of(2026, 9, 1), "4.58");

        assertThat(read()).extracting(MarketRateResponse::code).containsExactly("CDI", "IPCA");
    }

    @Test
    @DisplayName("requires a signed-in user")
    void requiresAuthentication() {
        ApiSession anonymous = newSession();

        ResponseEntity<String> response = anonymous.get("/market-rates", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
