package com.ir.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One computed ledger row. {@code seq} is the 1-based position within the
 * asset's ledger (not the global order sequence). BRL fields are null for BR
 * assets, whose native values are already in BRL. {@code taxRate} is present on
 * every SELL row; {@code taxDue} only when the taxable BRL gain is positive.
 */
public record LedgerLine(
        UUID orderId,
        int seq,
        OrderType orderType,
        LocalDate tradeDate,
        BigDecimal quantity,
        BigDecimal orderValue,
        BigDecimal pricePerShare,
        BigDecimal accumulatedQuantity,
        BigDecimal averagePrice,
        BigDecimal averagePriceBrl,
        BigDecimal orderValueBrl,
        BigDecimal accumulatedTotalCost,
        BigDecimal accumulatedTotalCostBrl,
        BigDecimal saleGain,
        BigDecimal saleGainBrl,
        BigDecimal taxRate,
        BigDecimal taxDue,
        PtaxQuote ptax
) {
}
