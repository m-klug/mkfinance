package com.ir.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A resolved PTAX rate for a specific order. {@code fallback} is true when the
 * quote comes from a later business day than the requested date (weekend/holiday).
 */
public record PtaxQuote(
        BigDecimal rate,
        PtaxRateType type,
        LocalDate quoteDate,
        LocalDate requestedDate,
        boolean fallback
) {
}
