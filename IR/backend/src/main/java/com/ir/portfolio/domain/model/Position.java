package com.ir.portfolio.domain.model;

import java.math.BigDecimal;

/**
 * Consolidated position of one asset at a point in time. BRL fields are null
 * for BR assets; for US assets with no orders they are zero (mirrors the
 * front-end contract).
 */
public record Position(
        Asset asset,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal totalCost,
        BigDecimal averagePriceBrl,
        BigDecimal totalCostBrl
) {
}
