package com.ir.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Input to the ledger engine. {@code seq} is the global insertion-order sequence
 * used as a tiebreak when two orders share the same trade date. For US assets,
 * {@code ptax} must be resolved by the caller before invoking the engine.
 */
public record OrderInput(
        UUID id,
        long seq,
        OrderType type,
        LocalDate tradeDate,
        BigDecimal quantity,
        BigDecimal orderValue,
        PtaxQuote ptax
) {
}
