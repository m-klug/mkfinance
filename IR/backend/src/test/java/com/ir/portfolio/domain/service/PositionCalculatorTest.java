package com.ir.portfolio.domain.service;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.AssetCategory;
import com.ir.portfolio.domain.model.Market;
import com.ir.portfolio.domain.model.OrderInput;
import com.ir.portfolio.domain.model.OrderType;
import com.ir.portfolio.domain.model.Position;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PositionCalculatorTest {

    private static final Asset ITSA4 =
            new Asset(UUID.randomUUID(), "ITSA4", "Itaúsa PN", Market.BR, AssetCategory.STOCK_BR);
    private static final Asset AAPL =
            new Asset(UUID.randomUUID(), "AAPL", "Apple Inc.", Market.US, AssetCategory.STOCK_US);

    private static OrderInput order(long seq, OrderType type, String date, String qty, String value) {
        return new OrderInput(UUID.randomUUID(), seq, type, LocalDate.parse(date),
                new BigDecimal(qty), new BigDecimal(value), null);
    }

    @Test
    void cutoffDateIsInclusive() {
        List<OrderInput> orders = List.of(
                order(1, OrderType.BUY, "2025-01-15", "100", "950"),
                order(2, OrderType.BUY, "2025-03-10", "100", "1050")
        );

        Position onFirstBuyDate = PositionCalculator.compute(orders, ITSA4, LocalDate.parse("2025-01-15"));
        assertThat(onFirstBuyDate.quantity()).isEqualByComparingTo("100");
        assertThat(onFirstBuyDate.averagePrice()).isEqualByComparingTo("9.50");

        Position beforeSecondBuy = PositionCalculator.compute(orders, ITSA4, LocalDate.parse("2025-03-09"));
        assertThat(beforeSecondBuy.quantity()).isEqualByComparingTo("100");

        Position after = PositionCalculator.compute(orders, ITSA4, LocalDate.parse("2025-03-10"));
        assertThat(after.quantity()).isEqualByComparingTo("200");
        assertThat(after.averagePrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void noCutoffUsesFullHistory() {
        List<OrderInput> orders = List.of(
                order(1, OrderType.BUY, "2025-01-15", "100", "950"),
                order(2, OrderType.SELL, "2025-06-05", "50", "600")
        );

        Position position = PositionCalculator.compute(orders, ITSA4, null);
        assertThat(position.quantity()).isEqualByComparingTo("50");
        assertThat(position.averagePriceBrl()).isNull();
        assertThat(position.totalCostBrl()).isNull();
    }

    @Test
    void emptyLedgerForUsAssetReturnsZeroBrlFields() {
        Position position = PositionCalculator.compute(List.of(), AAPL, null);

        assertThat(position.quantity()).isEqualByComparingTo("0");
        assertThat(position.averagePrice()).isEqualByComparingTo("0");
        assertThat(position.totalCost()).isEqualByComparingTo("0");
        assertThat(position.averagePriceBrl()).isEqualByComparingTo("0");
        assertThat(position.totalCostBrl()).isEqualByComparingTo("0");
    }

    @Test
    void emptyLedgerForBrAssetReturnsNullBrlFields() {
        Position position = PositionCalculator.compute(List.of(), ITSA4, null);

        assertThat(position.quantity()).isEqualByComparingTo("0");
        assertThat(position.averagePriceBrl()).isNull();
        assertThat(position.totalCostBrl()).isNull();
    }
}
