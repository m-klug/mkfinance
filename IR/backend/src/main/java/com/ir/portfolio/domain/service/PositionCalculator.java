package com.ir.portfolio.domain.service;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.LedgerLine;
import com.ir.portfolio.domain.model.OrderInput;
import com.ir.portfolio.domain.model.Position;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Consolidated position of one asset, optionally up to a cutoff date
 * (inclusive). Port of the front-end's {@code computePosicao}.
 */
public final class PositionCalculator {

    private PositionCalculator() {
    }

    public static Position compute(List<OrderInput> orders, Asset asset, LocalDate cutoffDate) {
        List<OrderInput> filtered = cutoffDate == null
                ? orders
                : orders.stream().filter(o -> !o.tradeDate().isAfter(cutoffDate)).toList();

        List<LedgerLine> ledger = LedgerEngine.buildLedger(filtered, asset);
        if (ledger.isEmpty()) {
            boolean isUs = asset.isUs();
            return new Position(
                    asset,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    isUs ? BigDecimal.ZERO : null,
                    isUs ? BigDecimal.ZERO : null
            );
        }

        LedgerLine last = ledger.getLast();
        return new Position(
                asset,
                last.accumulatedQuantity(),
                last.averagePrice(),
                last.accumulatedTotalCost(),
                last.averagePriceBrl(),
                last.accumulatedTotalCostBrl()
        );
    }
}
