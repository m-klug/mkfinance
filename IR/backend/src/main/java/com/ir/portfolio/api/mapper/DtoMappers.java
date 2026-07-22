package com.ir.portfolio.api.mapper;

import com.ir.portfolio.api.dto.AssetDtos.AssetResponse;
import com.ir.portfolio.api.dto.LedgerDtos.LedgerLineResponse;
import com.ir.portfolio.api.dto.OrderDtos.OrderResponse;
import com.ir.portfolio.api.dto.PositionDtos.PositionResponse;
import com.ir.portfolio.api.dto.PositionDtos.PositionValues;
import com.ir.portfolio.api.dto.PtaxDtos.PtaxInfo;
import com.ir.portfolio.api.dto.PtaxDtos.PtaxResponse;
import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.LedgerLine;
import com.ir.portfolio.domain.model.Position;
import com.ir.portfolio.domain.model.PtaxQuote;
import com.ir.portfolio.domain.service.CalcScale;
import com.ir.portfolio.infrastructure.persistence.entity.OrderEntity;

import java.math.BigDecimal;

/**
 * Domain → DTO mapping. This is the single place where internal-scale values
 * are rounded to presentation scales: money at 2 decimals, unit prices at up
 * to 6, quantities at up to 6, PTAX rates at 4.
 */
public final class DtoMappers {

    private DtoMappers() {
    }

    public static AssetResponse toResponse(Asset asset) {
        return new AssetResponse(asset.id(), asset.ticker(), asset.name(), asset.market(), asset.category());
    }

    public static OrderResponse toResponse(OrderEntity order, String ticker) {
        return new OrderResponse(
                order.getId(),
                order.getSeq(),
                ticker,
                order.getOrderType(),
                order.getTradeDate(),
                quantity(order.getQuantity()),
                money(order.getOrderValue()));
    }

    public static LedgerLineResponse toResponse(LedgerLine line) {
        return new LedgerLineResponse(
                line.orderId(),
                line.seq(),
                line.orderType(),
                line.tradeDate(),
                quantity(line.quantity()),
                money(line.orderValue()),
                price(line.pricePerShare()),
                quantity(line.accumulatedQuantity()),
                price(line.averagePrice()),
                price(line.averagePriceBrl()),
                money(line.orderValueBrl()),
                money(line.accumulatedTotalCost()),
                money(line.accumulatedTotalCostBrl()),
                money(line.saleGain()),
                money(line.saleGainBrl()),
                line.taxRate(),
                money(line.taxDue()),
                toInfo(line.ptax()));
    }

    public static PositionResponse toResponse(Position position) {
        return new PositionResponse(
                toResponse(position.asset()),
                quantity(position.quantity()),
                price(position.averagePrice()),
                money(position.totalCost()),
                price(position.averagePriceBrl()),
                money(position.totalCostBrl()));
    }

    public static PositionValues toValues(Position position) {
        return new PositionValues(
                quantity(position.quantity()),
                price(position.averagePrice()),
                money(position.totalCost()),
                price(position.averagePriceBrl()),
                money(position.totalCostBrl()));
    }

    public static PtaxResponse toResponse(PtaxQuote quote) {
        return new PtaxResponse(
                rate(quote.rate()), quote.type(), quote.quoteDate(), quote.requestedDate(), quote.fallback());
    }

    public static PtaxInfo toInfo(PtaxQuote quote) {
        return quote == null
                ? null
                : new PtaxInfo(rate(quote.rate()), quote.type(), quote.quoteDate(), quote.fallback());
    }

    /** Money values: fixed 2 decimals. */
    public static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(CalcScale.MONEY_SCALE, CalcScale.ROUNDING);
    }

    /** Unit prices: up to 6 decimals, at least 2. */
    public static BigDecimal price(BigDecimal value) {
        return normalize(value, CalcScale.PRICE_SCALE, CalcScale.MONEY_SCALE);
    }

    /** Quantities: up to 6 decimals, integers shown without decimals. */
    public static BigDecimal quantity(BigDecimal value) {
        return normalize(value, CalcScale.QUANTITY_SCALE, 0);
    }

    /** PTAX rates: fixed 4 decimals (BCB publication scale). */
    public static BigDecimal rate(BigDecimal value) {
        return value == null ? null : value.setScale(CalcScale.RATE_SCALE, CalcScale.ROUNDING);
    }

    private static BigDecimal normalize(BigDecimal value, int roundScale, int minScale) {
        if (value == null) {
            return null;
        }
        BigDecimal rounded = value.setScale(roundScale, CalcScale.ROUNDING).stripTrailingZeros();
        return rounded.scale() < minScale ? rounded.setScale(minScale, CalcScale.ROUNDING) : rounded;
    }
}
