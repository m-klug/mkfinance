package com.ir.portfolio.api.dto;

import com.ir.portfolio.domain.model.OrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderRequest(
            @NotBlank String ticker,
            @NotNull OrderType orderType,
            @NotNull LocalDate tradeDate,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @PositiveOrZero BigDecimal orderValue
    ) {
    }

    /** Partial update; null fields keep their current value. Ticker is immutable. */
    public record OrderPatchRequest(
            OrderType orderType,
            LocalDate tradeDate,
            @Positive BigDecimal quantity,
            @PositiveOrZero BigDecimal orderValue
    ) {
    }

    public record OrderResponse(
            UUID id,
            long seq,
            String ticker,
            OrderType orderType,
            LocalDate tradeDate,
            BigDecimal quantity,
            BigDecimal orderValue
    ) {
    }
}
