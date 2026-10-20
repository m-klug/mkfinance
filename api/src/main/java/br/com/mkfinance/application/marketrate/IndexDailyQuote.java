package br.com.mkfinance.application.marketrate;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One business day of an index as the source published it. */
public record IndexDailyQuote(LocalDate date, BigDecimal dailyPercent) {
}
