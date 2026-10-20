package br.com.mkfinance.fixedincome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import br.com.mkfinance.domain.service.fixedincome.BrazilianBusinessDays;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomePositionValuator;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomePositionValuator.PositionTerms;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomePositionValuator.Valuation;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeResult;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeSimulator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** What a bought title is worth on a day: real index for the days lived, a constant for the rest. */
class FixedIncomePositionValuatorTest {

    private static final LocalDate PURCHASE = LocalDate.of(2027, 1, 4);
    private static final LocalDate MATURITY = PURCHASE.plusMonths(12);
    private static final long PRINCIPAL = 1_000_000L;
    private static final BigDecimal CDI = new BigDecimal("13.65");

    private static PositionTerms terms(InstrumentType type, Indexer indexer, String rate) {
        return new PositionTerms(type, indexer, new BigDecimal(rate), PRINCIPAL, PURCHASE, MATURITY);
    }

    /** The CDI as a constant, expressed day by day, for every business day in the range. */
    private static NavigableMap<LocalDate, BigDecimal> flatSeries(LocalDate from, LocalDate to, BigDecimal annualPercent) {
        double daily = (Math.pow(1 + annualPercent.doubleValue() / 100, 1.0 / 252) - 1) * 100;
        return series(from, to, daily);
    }

    private static NavigableMap<LocalDate, BigDecimal> series(LocalDate from, LocalDate to, double dailyPercent) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (BrazilianBusinessDays.isBusinessDay(day)) {
                map.put(day, BigDecimal.valueOf(dailyPercent));
            }
        }
        return map;
    }

    private static FixedIncomeResult simulate(InstrumentType type, Indexer indexer, String rate) {
        return FixedIncomeSimulator.simulate(new FixedIncomeParams(type, indexer, new BigDecimal(rate), PRINCIPAL, 0, 12,
                PURCHASE, indexer == Indexer.CDI_PERCENT ? CDI : null, indexer == Indexer.SELIC_PERCENT ? CDI : null, null));
    }

    @Nested
    @DisplayName("agrees with the simulator when nothing differs from the constant")
    class AgreesWithSimulator {

        @Test
        @DisplayName("a fixed-rate title valued at maturity is what the simulator projects")
        void prefixed() {
            PositionTerms terms = terms(InstrumentType.CDB, Indexer.PREFIXED, "12");

            Valuation valuation = FixedIncomePositionValuator.valueOn(terms, MATURITY, PURCHASE, new TreeMap<>(), null);

            FixedIncomeResult projected = simulate(InstrumentType.CDB, Indexer.PREFIXED, "12");
            assertThat(valuation.grossCents()).isEqualTo(projected.grossCents());
            assertThat(valuation.netCents()).isEqualTo(projected.netCents());
            assertThat(valuation.incomeTaxCents()).isEqualTo(projected.incomeTaxCents());
        }

        @Test
        @DisplayName("a % of CDI title with all of its days still to come is what the simulator projects")
        void allFuture() {
            PositionTerms terms = terms(InstrumentType.CDB, Indexer.CDI_PERCENT, "110");

            Valuation valuation = FixedIncomePositionValuator.valueOn(terms, MATURITY, PURCHASE, new TreeMap<>(), CDI);

            assertThat(valuation.netCents()).isCloseTo(simulate(InstrumentType.CDB, Indexer.CDI_PERCENT, "110").netCents(), within(1L));
        }

        @Test
        @DisplayName("a real index that happens to equal the constant changes nothing, however the days are split")
        void flatHistoryEqualsConstant() {
            PositionTerms terms = terms(InstrumentType.CDB, Indexer.CDI_PERCENT, "110");
            LocalDate today = PURCHASE.plusMonths(5);

            Valuation valuation = FixedIncomePositionValuator.valueOn(terms, MATURITY, today, flatSeries(PURCHASE, today, CDI), CDI);

            assertThat(valuation.netCents()).isCloseTo(simulate(InstrumentType.CDB, Indexer.CDI_PERCENT, "110").netCents(), within(1L));
        }

        @Test
        @DisplayName("no stored history at all falls back to the constant for every day")
        void emptyHistory() {
            PositionTerms terms = terms(InstrumentType.CDB, Indexer.CDI_PERCENT, "110");

            Valuation valuation = FixedIncomePositionValuator.valueOn(terms, MATURITY, PURCHASE.plusMonths(5), new TreeMap<>(), CDI);

            assertThat(valuation.netCents()).isCloseTo(simulate(InstrumentType.CDB, Indexer.CDI_PERCENT, "110").netCents(), within(1L));
            assertThat(valuation.lastIndexDate()).isNull();
        }

        @Test
        @DisplayName("a Selic title works the same way with the Selic")
        void selic() {
            PositionTerms terms = terms(InstrumentType.TESOURO_SELIC, Indexer.SELIC_PERCENT, "100");

            Valuation valuation = FixedIncomePositionValuator.valueOn(terms, MATURITY, PURCHASE, new TreeMap<>(), CDI);

            FixedIncomeResult projected = simulate(InstrumentType.TESOURO_SELIC, Indexer.SELIC_PERCENT, "100");
            assertThat(valuation.netCents()).isCloseTo(projected.netCents(), within(1L));
            assertThat(valuation.custodyCents()).isPositive();
        }
    }

    @Nested
    @DisplayName("the real index is what counts for the days already lived")
    class RealHistory {

        @Test
        @DisplayName("a real index above the constant makes the title worth more than the projection")
        void higherHistory() {
            PositionTerms terms = terms(InstrumentType.LCI, Indexer.CDI_PERCENT, "100");
            LocalDate today = PURCHASE.plusMonths(6);
            NavigableMap<LocalDate, BigDecimal> hotter = flatSeries(PURCHASE, today, new BigDecimal("20"));

            long real = FixedIncomePositionValuator.valueOn(terms, MATURITY, today, hotter, CDI).netCents();
            long constant = FixedIncomePositionValuator.valueOn(terms, MATURITY, today, flatSeries(PURCHASE, today, CDI), CDI).netCents();

            assertThat(real).isGreaterThan(constant);
        }

        @Test
        @DisplayName("a valuation in the past is exactly the product of the stored days")
        void pastIsTheProductOfStoredDays() {
            PositionTerms terms = terms(InstrumentType.LCI, Indexer.CDI_PERCENT, "100");
            LocalDate asOf = PURCHASE.plusDays(40);
            // A different value on each day, so a wrong day would show up.
            NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
            double expected = 1;
            double daily = 0.04;
            for (LocalDate day = PURCHASE; day.isBefore(asOf); day = day.plusDays(1)) {
                if (BrazilianBusinessDays.isBusinessDay(day)) {
                    series.put(day, BigDecimal.valueOf(daily));
                    expected *= 1 + daily / 100;
                    daily += 0.0005;
                }
            }

            Valuation valuation = FixedIncomePositionValuator.valueOn(terms, asOf, asOf.plusDays(1), series, null);

            assertThat(valuation.grossCents()).isEqualTo(Math.round(PRINCIPAL * expected));
        }

        @Test
        @DisplayName("a percentage scales the day's index: 50% of the CDI yields about half")
        void percentageScalesTheDay() {
            LocalDate today = PURCHASE.plusMonths(3);
            NavigableMap<LocalDate, BigDecimal> series = flatSeries(PURCHASE, today, CDI);

            long full = FixedIncomePositionValuator.valueOn(terms(InstrumentType.LCI, Indexer.CDI_PERCENT, "100"), today, today, series, CDI).grossYieldCents();
            long half = FixedIncomePositionValuator.valueOn(terms(InstrumentType.LCI, Indexer.CDI_PERCENT, "50"), today, today, series, CDI).grossYieldCents();

            assertThat((double) half / full).isCloseTo(0.5, within(0.01));
        }

        @Test
        @DisplayName("business days the provider has not published yet carry the latest value forward")
        void unpublishedDaysCarryForward() {
            PositionTerms terms = terms(InstrumentType.LCI, Indexer.CDI_PERCENT, "100");
            LocalDate today = LocalDate.of(2027, 3, 3);
            NavigableMap<LocalDate, BigDecimal> complete = flatSeries(PURCHASE, today.minusDays(1), CDI);
            NavigableMap<LocalDate, BigDecimal> behind = new TreeMap<>(complete.headMap(LocalDate.of(2027, 3, 1), true));

            Valuation withGap = FixedIncomePositionValuator.valueOn(terms, today, today, behind, CDI);
            Valuation full = FixedIncomePositionValuator.valueOn(terms, today, today, complete, CDI);

            assertThat(withGap.grossCents()).isEqualTo(full.grossCents()); // flat series: the carried value is the same
            assertThat(withGap.lastIndexDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        }

        @Test
        @DisplayName("today itself counts as future: its own index is only published after the day")
        void todayIsFuture() {
            PositionTerms terms = terms(InstrumentType.LCI, Indexer.CDI_PERCENT, "100");
            LocalDate today = LocalDate.of(2027, 3, 3);
            NavigableMap<LocalDate, BigDecimal> series = flatSeries(PURCHASE, today.minusDays(1), CDI);
            series.put(today, new BigDecimal("5")); // an absurd value for today must not be used

            Valuation viaToday = FixedIncomePositionValuator.valueOn(terms, today.plusDays(1), today, series, CDI);

            assertThat(viaToday.grossCents()).isCloseTo(
                    FixedIncomePositionValuator.valueOn(terms, today.plusDays(1), today, flatSeries(PURCHASE, today.minusDays(1), CDI), CDI).grossCents(),
                    within(1L));
        }
    }

    @Nested
    @DisplayName("taxes follow the age of the title on the day it is valued")
    class Taxes {

        @Test
        @DisplayName("a CDB held 100 days pays 22,5% of the yield")
        void youngCdb() {
            PositionTerms terms = terms(InstrumentType.CDB, Indexer.PREFIXED, "12");

            Valuation valuation = FixedIncomePositionValuator.valueOn(terms, PURCHASE.plusDays(100), PURCHASE, new TreeMap<>(), null);

            assertThat(valuation.iofCents()).isZero();
            assertThat(valuation.incomeTaxCents()).isEqualTo(Math.round(valuation.grossYieldCents() * 0.225));
            assertThat(valuation.netCents()).isEqualTo(valuation.grossCents() - valuation.incomeTaxCents());
        }

        @Test
        @DisplayName("the same title is taxed less valued at maturity than valued earlier")
        void taxFallsWithAge() {
            PositionTerms terms = terms(InstrumentType.CDB, Indexer.PREFIXED, "12");

            double early = effectiveTax(FixedIncomePositionValuator.valueOn(terms, PURCHASE.plusDays(100), PURCHASE, new TreeMap<>(), null));
            double atMaturity = effectiveTax(FixedIncomePositionValuator.valueOn(terms, MATURITY, PURCHASE, new TreeMap<>(), null));

            assertThat(atMaturity).isLessThan(early);
        }

        private double effectiveTax(Valuation v) {
            return (double) v.incomeTaxCents() / v.grossYieldCents();
        }

        @Test
        @DisplayName("an LCI pays no tax at any age")
        void lciExempt() {
            Valuation valuation = FixedIncomePositionValuator.valueOn(
                    terms(InstrumentType.LCI, Indexer.PREFIXED, "12"), PURCHASE.plusDays(100), PURCHASE, new TreeMap<>(), null);

            assertThat(valuation.incomeTaxCents()).isZero();
            assertThat(valuation.netCents()).isEqualTo(valuation.grossCents());
        }
    }

    @Nested
    @DisplayName("estimate")
    class EstimateTest {

        @Test
        @DisplayName("gives today's value and the value at maturity, today's being the smaller")
        void todayAndMaturity() {
            PositionTerms terms = terms(InstrumentType.CDB, Indexer.PREFIXED, "12");

            var estimate = FixedIncomePositionValuator.estimate(terms, PURCHASE.plusMonths(6), new TreeMap<>(), null);

            assertThat(estimate.today().asOf()).isEqualTo(PURCHASE.plusMonths(6));
            assertThat(estimate.atMaturity().asOf()).isEqualTo(MATURITY);
            assertThat(estimate.today().netCents()).isLessThan(estimate.atMaturity().netCents());
        }

        @Test
        @DisplayName("a title already due is valued at its maturity, not beyond")
        void dueTitleIsCappedAtMaturity() {
            PositionTerms terms = terms(InstrumentType.CDB, Indexer.PREFIXED, "12");

            var estimate = FixedIncomePositionValuator.estimate(terms, MATURITY.plusDays(20), new TreeMap<>(), null);

            assertThat(estimate.today().asOf()).isEqualTo(MATURITY);
            assertThat(estimate.today().netCents()).isEqualTo(estimate.atMaturity().netCents());
        }

        @Test
        @DisplayName("on the purchase day the title is worth what was paid")
        void purchaseDay() {
            Valuation valuation = FixedIncomePositionValuator.valueOn(
                    terms(InstrumentType.CDB, Indexer.PREFIXED, "12"), PURCHASE, PURCHASE, new TreeMap<>(), null);

            assertThat(valuation.grossCents()).isEqualTo(PRINCIPAL);
            assertThat(valuation.netCents()).isEqualTo(PRINCIPAL);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("a title linked to the IPCA is not valued yet")
        void ipcaRefused() {
            assertThatThrownBy(() -> terms(InstrumentType.TESOURO_IPCA, Indexer.IPCA_PLUS, "6"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("IPCA");
        }

        @Test
        @DisplayName("an indexer the instrument is not sold with is refused")
        void indexerMustFit() {
            assertThatThrownBy(() -> terms(InstrumentType.CDB, Indexer.SELIC_PERCENT, "100"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the maturity must be after the purchase and the principal positive")
        void termsBounds() {
            assertThatThrownBy(() -> new PositionTerms(InstrumentType.CDB, Indexer.PREFIXED, BigDecimal.TEN, PRINCIPAL, PURCHASE, PURCHASE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PositionTerms(InstrumentType.CDB, Indexer.PREFIXED, BigDecimal.TEN, 0, PURCHASE, MATURITY))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("valuing before the purchase is refused")
        void beforePurchase() {
            assertThatThrownBy(() -> FixedIncomePositionValuator.valueOn(
                            terms(InstrumentType.CDB, Indexer.PREFIXED, "12"), PURCHASE.minusDays(1), PURCHASE, new TreeMap<>(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("days to come with no current rate to project them are refused, not guessed")
        void futureNeedsRate() {
            assertThatThrownBy(() -> FixedIncomePositionValuator.valueOn(
                            terms(InstrumentType.CDB, Indexer.CDI_PERCENT, "110"), MATURITY, PURCHASE, new TreeMap<>(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
