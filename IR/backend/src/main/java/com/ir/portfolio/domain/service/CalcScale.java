package com.ir.portfolio.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Scale and rounding policy of the calculation engine. Accumulators run at a
 * high internal scale; rounding to presentation scales happens only at the DTO
 * boundary, never inside the accumulation loop.
 */
public final class CalcScale {

    public static final int INTERNAL_SCALE = 10;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final int MONEY_SCALE = 2;
    public static final int PRICE_SCALE = 6;
    public static final int QUANTITY_SCALE = 6;
    public static final int RATE_SCALE = 4;

    private CalcScale() {
    }

    public static BigDecimal div(BigDecimal dividend, BigDecimal divisor) {
        return dividend.divide(divisor, INTERNAL_SCALE, ROUNDING);
    }
}
