package br.com.mkfinance.infrastructure.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.application.marketrate.MarketRateQuote;
import br.com.mkfinance.domain.model.MarketRateCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading the SGS answer. Pure unit tests: no network. */
class BcbSgsRateSourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName("takes the newest point dated on or before today")
    void newestUpToToday() {
        String body = """
                [{"data":"16/10/2026","valor":"13.65"},{"data":"19/10/2026","valor":"13.70"},{"data":"20/10/2026","valor":"13.75"}]
                """;

        Optional<MarketRateQuote> quote = BcbSgsRateSource.parseLatest(body, TODAY, MAPPER);

        assertThat(quote).contains(new MarketRateQuote(LocalDate.of(2026, 10, 20), new BigDecimal("13.75")));
    }

    @Test
    @DisplayName("ignores points dated after today (the Selic series lists dates ahead)")
    void ignoresFuture() {
        String body = """
                [{"data":"19/10/2026","valor":"13.65"},{"data":"03/11/2026","valor":"14.00"},{"data":"04/11/2026","valor":"14.00"}]
                """;

        assertThat(BcbSgsRateSource.parseLatest(body, TODAY, MAPPER).orElseThrow().ratePercent())
                .isEqualByComparingTo("13.65");
    }

    @Test
    @DisplayName("does not rely on the order of the points")
    void orderIndependent() {
        String body = """
                [{"data":"20/10/2026","valor":"2"},{"data":"01/10/2026","valor":"1"}]
                """;

        assertThat(BcbSgsRateSource.parseLatest(body, TODAY, MAPPER).orElseThrow().referenceDate())
                .isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("keeps a negative value (monthly IPCA can deflate)")
    void negativeValue() {
        String body = "[{\"data\":\"01/08/2026\",\"valor\":\"-0.32\"}]";

        assertThat(BcbSgsRateSource.parseLatest(body, TODAY, MAPPER).orElseThrow().ratePercent())
                .isEqualByComparingTo("-0.32");
    }

    @Test
    @DisplayName("an empty list is no value, not an error")
    void emptyList() {
        assertThat(BcbSgsRateSource.parseLatest("[]", TODAY, MAPPER)).isEmpty();
    }

    @Test
    @DisplayName("an answer that is not a list of points is an error")
    void garbage() {
        assertThatThrownBy(() -> BcbSgsRateSource.parseLatest("{\"erro\":\"x\"}", TODAY, MAPPER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BcbSgsRateSource.parseLatest("<html>", TODAY, MAPPER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BcbSgsRateSource.parseLatest("[{\"data\":\"20/10/2026\",\"valor\":\"abc\"}]", TODAY, MAPPER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("each rate maps to its SGS series")
    void seriesMapping() {
        assertThat(BcbSgsRateSource.seriesOf(MarketRateCode.CDI)).isEqualTo(4389);
        assertThat(BcbSgsRateSource.seriesOf(MarketRateCode.SELIC)).isEqualTo(432);
        assertThat(BcbSgsRateSource.seriesOf(MarketRateCode.IPCA)).isEqualTo(13522);
    }
}
