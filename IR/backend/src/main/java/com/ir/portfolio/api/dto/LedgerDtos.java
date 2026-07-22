package com.ir.portfolio.api.dto;

import com.ir.portfolio.domain.model.OrderType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record LedgerLineResponse(
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
            PtaxDtos.PtaxInfo ptax
    ) {
    }
}
