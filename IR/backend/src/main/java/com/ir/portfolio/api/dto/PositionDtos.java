package com.ir.portfolio.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class PositionDtos {

    private PositionDtos() {
    }

    public record PositionResponse(
            AssetDtos.AssetResponse asset,
            BigDecimal quantity,
            BigDecimal averagePrice,
            BigDecimal totalCost,
            BigDecimal averagePriceBrl,
            BigDecimal totalCostBrl
    ) {
    }

    public record PositionValues(
            BigDecimal quantity,
            BigDecimal averagePrice,
            BigDecimal totalCost,
            BigDecimal averagePriceBrl,
            BigDecimal totalCostBrl
    ) {
    }

    public record DashboardPositionResponse(
            AssetDtos.AssetResponse asset,
            PositionValues current,
            PositionValues atReference
    ) {
    }

    public record DashboardResponse(
            LocalDate referenceDate,
            List<DashboardPositionResponse> positions,
            BigDecimal totalCostBrazilBrl,
            BigDecimal totalCostAbroadBrl
    ) {
    }
}
