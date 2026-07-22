package com.ir.portfolio.api.dto;

import com.ir.portfolio.domain.model.Market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CapitalGainsDtos {

    private CapitalGainsDtos() {
    }

    public record SaleResponse(
            String ticker,
            String assetName,
            Market market,
            LocalDate tradeDate,
            BigDecimal quantity,
            BigDecimal pricePerShare,
            BigDecimal averagePrice,
            BigDecimal saleGain,
            BigDecimal saleGainBrl,
            BigDecimal taxableGainBrl,
            BigDecimal taxRate,
            BigDecimal taxDue
    ) {
    }

    public record MonthResponse(
            String month,
            List<SaleResponse> sales,
            BigDecimal totalResultBrl,
            BigDecimal totalTaxBrl,
            long lossOperationsCount
    ) {
    }

    public record YearResponse(
            int year,
            List<MonthResponse> months,
            BigDecimal totalResultBrl,
            BigDecimal totalTaxBrl,
            long lossOperationsCount
    ) {
    }
}
