package br.com.mkfinance.application.marketrate;

import br.com.mkfinance.domain.model.MarketRateCode;
import java.time.LocalDate;
import java.util.Optional;

/** Where market rates come from. One implementation per provider; tests swap in a fake. */
public interface MarketRateSource {

    /**
     * The latest published value of {@code code} dated on or before {@code today}.
     *
     * @return empty when the provider has no usable value
     * @throws RuntimeException when the provider cannot be reached or answers something unreadable
     */
    Optional<MarketRateQuote> latest(MarketRateCode code, LocalDate today);
}
