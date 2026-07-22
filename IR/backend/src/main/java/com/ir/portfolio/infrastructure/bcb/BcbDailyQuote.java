package com.ir.portfolio.infrastructure.bcb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * One row of the BCB Olinda PTAX response. Field names mirror the BCB wire
 * format (Portuguese); translation to the domain happens in PtaxService.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BcbDailyQuote(
        BigDecimal cotacaoCompra,
        BigDecimal cotacaoVenda,
        String dataHoraCotacao
) {
    /** BCB sends a variable number of fractional-second digits (e.g. ".9", ".243"). */
    private static final DateTimeFormatter BCB_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();

    public LocalDateTime timestamp() {
        return LocalDateTime.parse(dataHoraCotacao, BCB_TIMESTAMP);
    }
}
