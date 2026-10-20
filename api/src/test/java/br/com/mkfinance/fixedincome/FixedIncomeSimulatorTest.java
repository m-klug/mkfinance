package br.com.mkfinance.fixedincome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import br.com.mkfinance.domain.service.fixedincome.BrazilianBusinessDays;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeResult;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeSimulator;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Fixed-income projection: accrual, taxes, custody, contributions. Pure unit tests. */
class FixedIncomeSimulatorTest {

    private static final LocalDate START = LocalDate.of(2027, 1, 4);
    private static final long TEN_THOUSAND = 1_000_000L; // cents

    private static FixedIncomeParams prefixed(InstrumentType type, String rate, long initial, long monthly, int months, LocalDate start) {
        return new FixedIncomeParams(type, Indexer.PREFIXED, new BigDecimal(rate), initial, monthly, months, start, null, null, null);
    }

    private static void assertInternallyConsistent(FixedIncomeResult r) {
        assertThat(r.grossCents() - r.iofCents() - r.incomeTaxCents() - r.custodyCents()).isEqualTo(r.netCents());
        assertThat(r.grossCents() - r.investedCents()).isEqualTo(r.grossYieldCents());
        assertThat(r.netCents() - r.investedCents()).isEqualTo(r.netYieldCents());
    }

    @Nested
    @DisplayName("gross accrual")
    class GrossAccrual {

        @Test
        @DisplayName("prefixed: 12% a.a. compounds over the business days of the term")
        void prefixedAccrual() {
            FixedIncomeParams params = prefixed(InstrumentType.LCI, "12", TEN_THOUSAND, 0, 12, START);
            int days = BrazilianBusinessDays.countBetween(START, params.endDate());

            FixedIncomeResult result = FixedIncomeSimulator.simulate(params);

            long expected = Math.round(TEN_THOUSAND * Math.pow(1.12, days / 252.0));
            assertThat(result.grossCents()).isEqualTo(expected);
            assertThat(result.grossCents()).isBetween(1_115_000L, 1_125_000L);
        }

        @Test
        @DisplayName("% of CDI: 110% of a 10,65% CDI compounds the scaled daily rate")
        void cdiPercentAccrual() {
            FixedIncomeParams params = new FixedIncomeParams(InstrumentType.CDB, Indexer.CDI_PERCENT,
                    new BigDecimal("110"), TEN_THOUSAND, 0, 12, START, new BigDecimal("10.65"), null, null);
            int days = BrazilianBusinessDays.countBetween(START, params.endDate());

            FixedIncomeResult result = FixedIncomeSimulator.simulate(params);

            double daily = Math.pow(1.1065, 1.0 / 252) - 1;
            assertThat(result.grossCents()).isEqualTo(Math.round(TEN_THOUSAND * Math.pow(1 + daily * 1.10, days)));
        }

        @Test
        @DisplayName("IPCA+: the projected IPCA and the fixed part multiply")
        void ipcaPlusAccrual() {
            FixedIncomeParams params = new FixedIncomeParams(InstrumentType.TESOURO_IPCA, Indexer.IPCA_PLUS,
                    new BigDecimal("6"), TEN_THOUSAND, 0, 12, START, null, null, new BigDecimal("4"));
            int days = BrazilianBusinessDays.countBetween(START, params.endDate());

            FixedIncomeResult result = FixedIncomeSimulator.simulate(params);

            assertThat(result.grossCents()).isEqualTo(Math.round(TEN_THOUSAND * Math.pow(1.04 * 1.06, days / 252.0)));
        }

        @Test
        @DisplayName("Tesouro Selic follows the Selic")
        void selicPercentAccrual() {
            FixedIncomeParams params = new FixedIncomeParams(InstrumentType.TESOURO_SELIC, Indexer.SELIC_PERCENT,
                    new BigDecimal("100"), TEN_THOUSAND, 0, 12, START, null, new BigDecimal("14.50"), null);

            FixedIncomeResult result = FixedIncomeSimulator.simulate(params);

            assertThat(result.grossYieldCents()).isBetween(140_000L, 150_000L);
        }
    }

    @Nested
    @DisplayName("taxes")
    class Taxes {

        @Test
        @DisplayName("LCI and LCA are exempt from income tax and IOF")
        void exemptInstruments() {
            for (InstrumentType type : new InstrumentType[] {InstrumentType.LCI, InstrumentType.LCA}) {
                FixedIncomeResult result = FixedIncomeSimulator.simulate(prefixed(type, "12", TEN_THOUSAND, 0, 12, START));
                assertThat(result.incomeTaxCents()).isZero();
                assertThat(result.iofCents()).isZero();
                assertThat(result.netCents()).isEqualTo(result.grossCents());
                assertInternallyConsistent(result);
            }
        }

        @Test
        @DisplayName("CDB held 365 days pays 17,5% on the yield")
        void cdbBracketAtOneYear() {
            FixedIncomeResult result = FixedIncomeSimulator.simulate(prefixed(InstrumentType.CDB, "12", TEN_THOUSAND, 0, 12, START));

            assertThat(result.iofCents()).isZero();
            assertThat(result.incomeTaxCents()).isEqualTo(Math.round(result.grossYieldCents() * 0.175));
            assertInternallyConsistent(result);
        }

        @Test
        @DisplayName("each bracket boundary: 180 -> 22,5%, 181 -> 20%, 360 -> 20%, 361 -> 17,5%, 720 -> 17,5%, 721 -> 15%")
        void bracketBoundaries() {
            // Direct check of the table through terms whose length falls on each side of a boundary.
            // 6 months from 2027-01-04 = 181 days; 12 months = 365; 24 months = 730.
            assertThat(tax(6)).isEqualTo(0.20);
            assertThat(tax(12)).isEqualTo(0.175);
            assertThat(tax(24)).isEqualTo(0.15);
            assertThat(tax(3)).isEqualTo(0.225);
        }

        private double tax(int months) {
            FixedIncomeResult r = FixedIncomeSimulator.simulate(prefixed(InstrumentType.CDB, "12", TEN_THOUSAND, 0, months, START));
            return Math.round((double) r.incomeTaxCents() / r.grossYieldCents() * 1000) / 1000.0;
        }

        @Test
        @DisplayName("below 30 days IOF takes a share of the yield first, then income tax on the rest")
        void iofBelowThirtyDays() {
            LocalDate start = LocalDate.of(2027, 2, 1); // 1 month = 28 days
            FixedIncomeResult result = FixedIncomeSimulator.simulate(prefixed(InstrumentType.CDB, "12", TEN_THOUSAND, 0, 1, start));

            long yield = result.grossYieldCents();
            long iof = Math.round(yield * 0.06); // day 28 -> 6%
            assertThat(result.iofCents()).isEqualTo(iof);
            assertThat(result.incomeTaxCents()).isEqualTo(Math.round((yield - iof) * 0.225));
            assertInternallyConsistent(result);
        }

        @Test
        @DisplayName("contributions of different ages fall in different brackets")
        void contributionsTaxedByOwnAge() {
            FixedIncomeResult result = FixedIncomeSimulator.simulate(prefixed(InstrumentType.CDB, "12", TEN_THOUSAND, 100_000L, 24, START));

            double effective = (double) result.incomeTaxCents() / result.grossYieldCents();
            assertThat(effective).isStrictlyBetween(0.15, 0.175);
            assertInternallyConsistent(result);
        }
    }

    @Nested
    @DisplayName("custody")
    class Custody {

        @Test
        @DisplayName("Tesouro pays B3 custody, bank papers do not")
        void onlyTesouroPaysCustody() {
            FixedIncomeResult tesouro = FixedIncomeSimulator.simulate(prefixed(InstrumentType.TESOURO_PREFIXADO, "12", TEN_THOUSAND, 0, 12, START));
            FixedIncomeResult cdb = FixedIncomeSimulator.simulate(prefixed(InstrumentType.CDB, "12", TEN_THOUSAND, 0, 12, START));

            assertThat(tesouro.custodyCents()).isBetween(2_000L, 2_500L); // ~0,20% of ~11.200
            assertThat(cdb.custodyCents()).isZero();
            assertInternallyConsistent(tesouro);
        }
    }

    @Nested
    @DisplayName("contributions and series")
    class ContributionsAndSeries {

        @Test
        @DisplayName("the monthly contribution is applied from month 1 up to the month before the end")
        void contributionSchedule() {
            FixedIncomeResult result = FixedIncomeSimulator.simulate(prefixed(InstrumentType.LCI, "12", 0, 100_000L, 3, START));

            assertThat(result.investedCents()).isEqualTo(200_000L); // months 1 and 2
            assertThat(result.series()).hasSize(4);
            assertThat(result.series().get(0).investedCents()).isZero();
            assertThat(result.series().get(1).investedCents()).isEqualTo(100_000L);
            assertThat(result.series().get(3).investedCents()).isEqualTo(200_000L);
        }

        @Test
        @DisplayName("the series starts at the start date and ends at the redemption with the final result")
        void seriesBookends() {
            FixedIncomeParams params = prefixed(InstrumentType.CDB, "12", TEN_THOUSAND, 50_000L, 18, START);

            FixedIncomeResult result = FixedIncomeSimulator.simulate(params);

            assertThat(result.series().get(0).date()).isEqualTo(START);
            assertThat(result.series().get(0).grossCents()).isEqualTo(TEN_THOUSAND);
            var last = result.series().get(18);
            assertThat(last.date()).isEqualTo(params.endDate());
            assertThat(last.grossCents()).isEqualTo(result.grossCents());
            assertThat(last.netCents()).isEqualTo(result.netCents());
        }

        @Test
        @DisplayName("gross balance never falls along the term")
        void grossGrows() {
            FixedIncomeResult result = FixedIncomeSimulator.simulate(prefixed(InstrumentType.CDB, "10", TEN_THOUSAND, 20_000L, 36, START));

            for (int i = 1; i < result.series().size(); i++) {
                assertThat(result.series().get(i).grossCents()).isGreaterThanOrEqualTo(result.series().get(i - 1).grossCents());
            }
        }
    }

    @Nested
    @DisplayName("net annual equivalent")
    class Equivalent {

        @Test
        @DisplayName("an exempt prefixed paper is worth about its own rate")
        void exemptEqualsNominal() {
            FixedIncomeResult result = FixedIncomeSimulator.simulate(prefixed(InstrumentType.LCI, "12", TEN_THOUSAND, 0, 12, START));

            assertThat(result.netAnnualEquivalentPercent()).isCloseTo(12.0, within(0.3));
        }

        @Test
        @DisplayName("tax makes the equivalent rate lower than the gross one")
        void taxLowersEquivalent() {
            FixedIncomeResult cdb = FixedIncomeSimulator.simulate(prefixed(InstrumentType.CDB, "12", TEN_THOUSAND, 0, 12, START));
            FixedIncomeResult lci = FixedIncomeSimulator.simulate(prefixed(InstrumentType.LCI, "12", TEN_THOUSAND, 0, 12, START));

            assertThat(cdb.netAnnualEquivalentPercent()).isLessThan(lci.netAnnualEquivalentPercent());
        }

        @Test
        @DisplayName("LCI at 90% of the CDI ties a CDB at about 109% (90 / (1 - 17,5%)) over one year")
        void lciVersusCdb() {
            BigDecimal cdi = new BigDecimal("14.90");
            FixedIncomeResult lci90 = cdi(InstrumentType.LCI, "90", cdi);
            FixedIncomeResult cdb105 = cdi(InstrumentType.CDB, "105", cdi);
            FixedIncomeResult cdb113 = cdi(InstrumentType.CDB, "113", cdi);

            assertThat(cdb105.netCents()).isLessThan(lci90.netCents());
            assertThat(cdb113.netCents()).isGreaterThan(lci90.netCents());
        }

        private FixedIncomeResult cdi(InstrumentType type, String percent, BigDecimal cdi) {
            return FixedIncomeSimulator.simulate(new FixedIncomeParams(type, Indexer.CDI_PERCENT, new BigDecimal(percent),
                    TEN_THOUSAND, 0, 12, START, cdi, null, null));
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("an indexer the instrument is not sold with is refused")
        void indexerMustFitInstrument() {
            assertThatThrownBy(() -> new FixedIncomeParams(InstrumentType.CDB, Indexer.SELIC_PERCENT, BigDecimal.TEN,
                    TEN_THOUSAND, 0, 12, START, null, BigDecimal.TEN, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new FixedIncomeParams(InstrumentType.TESOURO_SELIC, Indexer.PREFIXED, BigDecimal.TEN,
                    TEN_THOUSAND, 0, 12, START, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the assumption an indexer needs must be given")
        void assumptionRequired() {
            assertThatThrownBy(() -> new FixedIncomeParams(InstrumentType.CDB, Indexer.CDI_PERCENT, BigDecimal.TEN,
                    TEN_THOUSAND, 0, 12, START, null, null, null)).hasMessageContaining("cdiAnnualPercent");
            assertThatThrownBy(() -> new FixedIncomeParams(InstrumentType.TESOURO_IPCA, Indexer.IPCA_PLUS, BigDecimal.TEN,
                    TEN_THOUSAND, 0, 12, START, null, null, null)).hasMessageContaining("ipcaAnnualPercent");
        }

        @Test
        @DisplayName("term, amounts and rate are bounded")
        void bounds() {
            assertThatThrownBy(() -> prefixed(InstrumentType.CDB, "12", TEN_THOUSAND, 0, 0, START)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> prefixed(InstrumentType.CDB, "12", TEN_THOUSAND, 0, 601, START)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> prefixed(InstrumentType.CDB, "12", 0, 0, 12, START)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> prefixed(InstrumentType.CDB, "-1", TEN_THOUSAND, 0, 12, START)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> prefixed(InstrumentType.CDB, "12", -1, 100, 12, START)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("one month with only a monthly contribution would apply nothing")
        void nothingApplied() {
            assertThatThrownBy(() -> prefixed(InstrumentType.CDB, "12", 0, 100_000L, 1, START))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
