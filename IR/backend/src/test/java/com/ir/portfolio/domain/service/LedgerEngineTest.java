package com.ir.portfolio.domain.service;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.AssetCategory;
import com.ir.portfolio.domain.model.LedgerLine;
import com.ir.portfolio.domain.model.Market;
import com.ir.portfolio.domain.model.OrderInput;
import com.ir.portfolio.domain.model.OrderType;
import com.ir.portfolio.domain.model.PtaxQuote;
import com.ir.portfolio.domain.model.PtaxRateType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.ir.portfolio.domain.model.OrderType.AMORTIZATION;
import static com.ir.portfolio.domain.model.OrderType.BONUS_SHARES;
import static com.ir.portfolio.domain.model.OrderType.BUY;
import static com.ir.portfolio.domain.model.OrderType.REVERSE_SPLIT;
import static com.ir.portfolio.domain.model.OrderType.SELL;
import static com.ir.portfolio.domain.model.OrderType.SPLIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Expected values mirror the seed scenarios of the front-end mock
 * (app/src/lib/mockData.ts) plus edge cases of calc.ts.
 */
class LedgerEngineTest {

    private static final Asset ITSA4 = brAsset("ITSA4", AssetCategory.STOCK_BR);
    private static final Asset PETR4 = brAsset("PETR4", AssetCategory.STOCK_BR);
    private static final Asset MXRF11 = brAsset("MXRF11", AssetCategory.FII);
    private static final Asset HGLG11 = brAsset("HGLG11", AssetCategory.FII);
    private static final Asset BOVA11 = brAsset("BOVA11", AssetCategory.ETF_BR);
    private static final Asset AAPL = new Asset(UUID.randomUUID(), "AAPL", "Apple Inc.", Market.US, AssetCategory.STOCK_US);

    private static Asset brAsset(String ticker, AssetCategory category) {
        return new Asset(UUID.randomUUID(), ticker, ticker, Market.BR, category);
    }

    private static OrderInput order(long seq, OrderType type, String date, String qty, String value) {
        return new OrderInput(UUID.randomUUID(), seq, type, LocalDate.parse(date),
                new BigDecimal(qty), new BigDecimal(value), null);
    }

    private static OrderInput usOrder(long seq, OrderType type, String date, String qty, String value, String rate) {
        PtaxRateType rateType = CapitalGainsTaxRate.ptaxRateTypeFor(type);
        PtaxQuote ptax = new PtaxQuote(new BigDecimal(rate), rateType, LocalDate.parse(date), LocalDate.parse(date), false);
        return new OrderInput(UUID.randomUUID(), seq, type, LocalDate.parse(date),
                new BigDecimal(qty), new BigDecimal(value), ptax);
    }

    private static void assertValue(BigDecimal actual, String expected) {
        assertThat(actual).isNotNull();
        assertThat(actual).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void itsa4WeightedAverageAndSaleGain() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                order(1, BUY, "2025-01-15", "100", "950"),
                order(2, BUY, "2025-03-10", "100", "1050"),
                order(3, SELL, "2025-06-05", "50", "600"),
                order(4, BUY, "2026-02-10", "50", "550")
        ), ITSA4);

        assertThat(ledger).hasSize(4);

        assertValue(ledger.get(0).averagePrice(), "9.50");
        assertValue(ledger.get(0).accumulatedQuantity(), "100");
        assertValue(ledger.get(0).accumulatedTotalCost(), "950");

        assertValue(ledger.get(1).averagePrice(), "10.00");
        assertValue(ledger.get(1).accumulatedQuantity(), "200");
        assertValue(ledger.get(1).accumulatedTotalCost(), "2000");

        LedgerLine sale = ledger.get(2);
        assertValue(sale.pricePerShare(), "12.00");
        assertValue(sale.saleGain(), "100");
        assertValue(sale.taxRate(), "0.15");
        assertValue(sale.taxDue(), "15");
        assertValue(sale.averagePrice(), "10.00");
        assertValue(sale.accumulatedQuantity(), "150");

        assertValue(ledger.get(3).averagePrice(), "10.25");
        assertValue(ledger.get(3).accumulatedQuantity(), "200");
        assertValue(ledger.get(3).accumulatedTotalCost(), "2050");
    }

    @Test
    void petr4BonusSharesDiluteAveragePrice() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                order(1, BUY, "2025-04-02", "200", "6400"),
                order(2, BUY, "2025-09-12", "100", "3550"),
                order(3, BONUS_SHARES, "2025-11-03", "10", "0")
        ), PETR4);

        assertValue(ledger.get(0).averagePrice(), "32.00");
        // (32.00×200 + 3550) / 300 = 9950/300
        assertThat(ledger.get(1).averagePrice().setScale(6, CalcScale.ROUNDING))
                .isEqualByComparingTo(new BigDecimal("33.166667"));
        // bonus at zero cost dilutes: ~9950/310
        assertThat(ledger.get(2).averagePrice().setScale(6, CalcScale.ROUNDING))
                .isEqualByComparingTo(new BigDecimal("32.096774"));
        assertValue(ledger.get(2).accumulatedQuantity(), "310");
    }

    @Test
    void mxrf11AmortizationReducesAveragePriceAndFiiTaxRateApplies() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                order(1, BUY, "2025-02-01", "200", "2000"),
                order(2, AMORTIZATION, "2025-03-01", "200", "40"),
                order(3, SELL, "2026-03-15", "50", "575")
        ), MXRF11);

        assertValue(ledger.get(0).averagePrice(), "10.00");

        LedgerLine amortization = ledger.get(1);
        assertValue(amortization.averagePrice(), "9.80");
        assertValue(amortization.accumulatedQuantity(), "200");

        LedgerLine sale = ledger.get(2);
        assertValue(sale.pricePerShare(), "11.50");
        assertValue(sale.saleGain(), "85");
        assertValue(sale.taxRate(), "0.20");
        assertValue(sale.taxDue(), "17");
    }

    @Test
    void hglg11ReverseSplitPreservesTotalCost() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                order(1, BUY, "2025-05-20", "80", "13200"),
                order(2, REVERSE_SPLIT, "2025-10-01", "8", "0")
        ), HGLG11);

        assertValue(ledger.get(0).averagePrice(), "165");

        LedgerLine reverseSplit = ledger.get(1);
        assertValue(reverseSplit.accumulatedQuantity(), "72");
        assertThat(reverseSplit.averagePrice().setScale(6, CalcScale.ROUNDING))
                .isEqualByComparingTo(new BigDecimal("183.333333"));
        assertThat(reverseSplit.accumulatedTotalCost().setScale(2, CalcScale.ROUNDING))
                .isEqualByComparingTo(new BigDecimal("13200.00"));
    }

    @Test
    void bova11SplitPreservesTotalCost() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                order(1, BUY, "2025-02-15", "50", "4900"),
                order(2, SPLIT, "2025-08-01", "50", "0")
        ), BOVA11);

        assertValue(ledger.get(0).averagePrice(), "98");
        assertValue(ledger.get(1).accumulatedQuantity(), "100");
        assertValue(ledger.get(1).averagePrice(), "49");
        assertValue(ledger.get(1).accumulatedTotalCost(), "4900");
    }

    @Test
    void aaplDualCurrencyLedgerAccumulatesBrlIndependently() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                usOrder(1, BUY, "2025-01-10", "10", "1500", "5.10"),
                usOrder(2, SELL, "2025-06-20", "5", "900", "4.95"),
                usOrder(3, BUY, "2026-02-05", "5", "850", "5.00")
        ), AAPL);

        LedgerLine firstBuy = ledger.get(0);
        assertValue(firstBuy.averagePrice(), "150");
        assertValue(firstBuy.orderValueBrl(), "7650");
        assertValue(firstBuy.averagePriceBrl(), "765");
        assertValue(firstBuy.accumulatedTotalCostBrl(), "7650");

        LedgerLine sale = ledger.get(1);
        assertValue(sale.saleGain(), "150");
        assertValue(sale.orderValueBrl(), "4455");
        assertValue(sale.saleGainBrl(), "630");
        assertValue(sale.taxRate(), "0.15");
        // tax on the BRL gain, not the USD gain: 630 × 0.15
        assertValue(sale.taxDue(), "94.50");
        assertValue(sale.averagePrice(), "150");
        assertValue(sale.averagePriceBrl(), "765");
        assertValue(sale.accumulatedQuantity(), "5");
        assertValue(sale.accumulatedTotalCostBrl(), "3825");

        LedgerLine secondBuy = ledger.get(2);
        assertValue(secondBuy.averagePrice(), "160");
        assertValue(secondBuy.averagePriceBrl(), "807.50");
        assertValue(secondBuy.accumulatedQuantity(), "10");
    }

    @Test
    void sellAtLossHasTaxRateButNoTaxDue() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                order(1, BUY, "2025-01-15", "100", "1000"),
                order(2, SELL, "2025-06-05", "50", "400")
        ), ITSA4);

        LedgerLine sale = ledger.get(1);
        assertValue(sale.saleGain(), "-100");
        assertValue(sale.taxRate(), "0.15");
        assertThat(sale.taxDue()).isNull();
    }

    @Test
    void sameDateOrdersAreTieBrokenBySeq() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                order(2, SELL, "2025-03-10", "100", "1200"),
                order(1, BUY, "2025-03-10", "100", "1000")
        ), ITSA4);

        assertThat(ledger.get(0).orderType()).isEqualTo(BUY);
        assertThat(ledger.get(1).orderType()).isEqualTo(SELL);
        assertValue(ledger.get(1).saleGain(), "200");
    }

    @Test
    void amortizationBaseFallsBackToPreviousQuantityThenOne() {
        // quantity 0 → base = previous accumulated quantity (200)
        List<LedgerLine> withPosition = LedgerEngine.buildLedger(List.of(
                order(1, BUY, "2025-02-01", "200", "2000"),
                order(2, AMORTIZATION, "2025-03-01", "0", "40")
        ), MXRF11);
        assertValue(withPosition.get(1).averagePrice(), "9.80");

        // quantity 0 and no position → base = 1
        List<LedgerLine> noPosition = LedgerEngine.buildLedger(List.of(
                order(1, AMORTIZATION, "2025-03-01", "0", "40")
        ), MXRF11);
        assertValue(noPosition.get(0).averagePrice(), "-40");
    }

    @Test
    void bonusSharesAsFirstOrderUsesNewQuantityGuard() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                order(1, BONUS_SHARES, "2025-01-15", "10", "0")
        ), PETR4);

        assertValue(ledger.get(0).averagePrice(), "0");
        assertValue(ledger.get(0).accumulatedQuantity(), "10");
    }

    @Test
    void zeroQuantityOrderYieldsZeroPricePerShare() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                order(1, BUY, "2025-01-15", "0", "100")
        ), ITSA4);

        assertValue(ledger.get(0).pricePerShare(), "0");
        assertValue(ledger.get(0).averagePrice(), "0");
    }

    @Test
    void usOrderWithoutPtaxIsRejected() {
        assertThatThrownBy(() -> LedgerEngine.buildLedger(List.of(
                order(1, BUY, "2025-01-10", "10", "1500")
        ), AAPL)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void brAssetHasNoBrlColumns() {
        List<LedgerLine> ledger = LedgerEngine.buildLedger(List.of(
                order(1, BUY, "2025-01-15", "100", "950")
        ), ITSA4);

        LedgerLine line = ledger.get(0);
        assertThat(line.averagePriceBrl()).isNull();
        assertThat(line.orderValueBrl()).isNull();
        assertThat(line.accumulatedTotalCostBrl()).isNull();
        assertThat(line.ptax()).isNull();
    }
}
