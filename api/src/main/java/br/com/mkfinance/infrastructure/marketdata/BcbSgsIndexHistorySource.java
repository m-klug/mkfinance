package br.com.mkfinance.infrastructure.marketdata;

import br.com.mkfinance.application.marketrate.IndexDailyQuote;
import br.com.mkfinance.application.marketrate.IndexHistorySource;
import br.com.mkfinance.domain.model.IndexCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads the daily CDI (SGS 12) and the daily Selic effective rate (SGS 11) from the Banco Central.
 *
 * <p>A long history is fetched in windows of {@code CHUNK_YEARS} years: the SGS answers a daily
 * series over a very wide range slowly, and a failed window would otherwise throw away everything
 * already read.
 */
@Component
public class BcbSgsIndexHistorySource implements IndexHistorySource {

    private static final int CHUNK_YEARS = 3;

    private final BcbSgsClient client;
    private final ObjectMapper mapper;

    public BcbSgsIndexHistorySource(BcbSgsClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public List<IndexDailyQuote> between(IndexCode code, LocalDate from, LocalDate to) {
        List<IndexDailyQuote> quotes = new ArrayList<>();
        LocalDate start = from;
        while (!start.isAfter(to)) {
            LocalDate end = start.plusYears(CHUNK_YEARS).minusDays(1);
            if (end.isAfter(to)) {
                end = to;
            }
            quotes.addAll(parse(client.fetch(seriesOf(code), start, end), mapper));
            start = end.plusDays(1);
        }
        return quotes;
    }

    static int seriesOf(IndexCode code) {
        return switch (code) {
            case CDI -> 12;
            case SELIC -> 11;
        };
    }

    /** Every point of the answer; a body that is not a list of points is an error, an empty list is not. */
    static List<IndexDailyQuote> parse(String body, ObjectMapper mapper) {
        try {
            JsonNode points = mapper.readTree(body);
            if (points == null || !points.isArray()) {
                throw new IllegalStateException("SGS answered something that is not a list");
            }
            List<IndexDailyQuote> quotes = new ArrayList<>();
            for (JsonNode point : points) {
                LocalDate date = LocalDate.parse(point.path("data").asText(), BcbSgsClient.BR_DATE);
                quotes.add(new IndexDailyQuote(date, new BigDecimal(point.path("valor").asText())));
            }
            return quotes;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Unreadable SGS answer", e);
        }
    }
}
