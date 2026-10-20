package br.com.mkfinance.application.marketrate;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A rate as the source published it, before it is stored. */
public record MarketRateQuote(LocalDate referenceDate, BigDecimal ratePercent) {
}
