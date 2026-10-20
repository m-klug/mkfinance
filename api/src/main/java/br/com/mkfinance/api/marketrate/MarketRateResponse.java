package br.com.mkfinance.api.marketrate;

import br.com.mkfinance.domain.model.MarketRate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Contract schema {@code MarketRate}.
 *
 * @param code {@code CDI} and {@code SELIC} in % a.a.; {@code IPCA} as the % accumulated in 12 months
 * @param referenceDate the date the Banco Central dates the value
 * @param fetchedAt when this app last confirmed it
 */
public record MarketRateResponse(String code, BigDecimal ratePercent, LocalDate referenceDate, OffsetDateTime fetchedAt) {

    public static MarketRateResponse of(MarketRate rate) {
        return new MarketRateResponse(rate.getCode().name(), rate.getRatePercent(), rate.getReferenceDate(), rate.getFetchedAt());
    }
}
