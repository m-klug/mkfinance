package br.com.mkfinance.infrastructure.marketdata;

import br.com.mkfinance.application.marketrate.MarketRateQuote;
import br.com.mkfinance.application.marketrate.MarketRateSource;
import br.com.mkfinance.domain.model.MarketRateCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reads the current CDI, Selic and IPCA from the Banco Central SGS open API (through
 * {@link BcbSgsClient}).
 *
 * <p>The last {@code WINDOW_DAYS} days up to today are requested and the newest point is taken. A
 * window by date is used rather than "the last N points" because the Selic series already lists
 * dates ahead of today, which would push every real value out of the last N.
 */
@Component
public class BcbSgsRateSource implements MarketRateSource {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Wide enough to hold a monthly series (IPCA is dated the 1st and published weeks later). */
    private static final int WINDOW_DAYS = 90;

    private final BcbSgsClient client;
    private final ObjectMapper mapper;

    public BcbSgsRateSource(BcbSgsClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Optional<MarketRateQuote> latest(MarketRateCode code, LocalDate today) {
        String body = client.fetch(seriesOf(code), today.minusDays(WINDOW_DAYS), today);
        return parseLatest(body, today, mapper);
    }

    static int seriesOf(MarketRateCode code) {
        return switch (code) {
            case CDI -> 4389;
            case SELIC -> 432;
            case IPCA -> 13522;
        };
    }

    /** The newest point dated on or before {@code today}; a malformed body is an error, an empty list is not. */
    static Optional<MarketRateQuote> parseLatest(String body, LocalDate today, ObjectMapper mapper) {
        try {
            JsonNode points = mapper.readTree(body);
            if (points == null || !points.isArray()) {
                throw new IllegalStateException("SGS answered something that is not a list");
            }
            MarketRateQuote latest = null;
            for (JsonNode point : points) {
                LocalDate date = LocalDate.parse(point.path("data").asText(), BR_DATE);
                if (date.isAfter(today)) {
                    continue;
                }
                if (latest == null || date.isAfter(latest.referenceDate())) {
                    latest = new MarketRateQuote(date, new BigDecimal(point.path("valor").asText()));
                }
            }
            return Optional.ofNullable(latest);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Unreadable SGS answer", e);
        }
    }
}
