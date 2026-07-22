package com.ir.portfolio.domain.service;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.AssetCategory;
import com.ir.portfolio.domain.model.OrderType;
import com.ir.portfolio.domain.model.PtaxRateType;

import java.math.BigDecimal;

public final class CapitalGainsTaxRate {

    private static final BigDecimal RATE_DEFAULT = new BigDecimal("0.15");
    private static final BigDecimal RATE_FII = new BigDecimal("0.20");

    private CapitalGainsTaxRate() {
    }

    /** US market → 15%; FII → 20%; BR stocks/ETFs/units → 15%. */
    public static BigDecimal forAsset(Asset asset) {
        if (asset.isUs()) {
            return RATE_DEFAULT;
        }
        if (asset.category() == AssetCategory.FII) {
            return RATE_FII;
        }
        return RATE_DEFAULT;
    }

    /**
     * Receita Federal rule: BUY orders convert at the PTAX sell rate; every
     * other order type converts at the PTAX buy rate.
     */
    public static PtaxRateType ptaxRateTypeFor(OrderType orderType) {
        return orderType == OrderType.BUY ? PtaxRateType.SELL : PtaxRateType.BUY;
    }
}
