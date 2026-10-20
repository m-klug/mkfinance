package br.com.mkfinance.infrastructure.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.application.marketrate.IndexDailyQuote;
import br.com.mkfinance.domain.model.IndexCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading the daily-index answers of the SGS. Pure unit tests: no network. */
class BcbSgsIndexHistorySourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("reads every point with its date and its percent-per-day")
    void readsEveryPoint() {
        String body = """
                [{"data":"16/10/2026","valor":"0.054266"},{"data":"19/10/2026","valor":"0.054300"}]
                """;

        List<IndexDailyQuote> quotes = BcbSgsIndexHistorySource.parse(body, MAPPER);

        assertThat(quotes).containsExactly(
                new IndexDailyQuote(LocalDate.of(2026, 10, 16), new BigDecimal("0.054266")),
                new IndexDailyQuote(LocalDate.of(2026, 10, 19), new BigDecimal("0.054300")));
    }

    @Test
    @DisplayName("an empty list is no data, not an error")
    void emptyList() {
        assertThat(BcbSgsIndexHistorySource.parse("[]", MAPPER)).isEmpty();
    }

    @Test
    @DisplayName("an answer that is not a list of points is an error")
    void garbage() {
        assertThatThrownBy(() -> BcbSgsIndexHistorySource.parse("{\"erro\":\"x\"}", MAPPER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BcbSgsIndexHistorySource.parse("<html>502</html>", MAPPER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BcbSgsIndexHistorySource.parse("[{\"data\":\"16/10/2026\",\"valor\":\"x\"}]", MAPPER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the CDI is SGS 12 and the Selic effective rate is SGS 11")
    void seriesMapping() {
        assertThat(BcbSgsIndexHistorySource.seriesOf(IndexCode.CDI)).isEqualTo(12);
        assertThat(BcbSgsIndexHistorySource.seriesOf(IndexCode.SELIC)).isEqualTo(11);
    }
}
