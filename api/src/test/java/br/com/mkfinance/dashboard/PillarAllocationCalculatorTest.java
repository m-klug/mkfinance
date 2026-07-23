package br.com.mkfinance.dashboard;

import static br.com.mkfinance.domain.model.Pillar.COMFORT;
import static br.com.mkfinance.domain.model.Pillar.FINANCIAL_FREEDOM;
import static br.com.mkfinance.domain.model.Pillar.FIXED_COSTS;
import static br.com.mkfinance.domain.model.Pillar.GOALS;
import static br.com.mkfinance.domain.model.Pillar.KNOWLEDGE;
import static br.com.mkfinance.domain.model.Pillar.PLEASURES;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.domain.service.PillarAllocationCalculator;
import br.com.mkfinance.domain.service.PillarAllocationCalculator.ContributionEntry;
import br.com.mkfinance.domain.service.PillarAllocationCalculator.ExpenseEntry;
import br.com.mkfinance.domain.service.PillarAllocationCalculator.PillarLine;
import br.com.mkfinance.domain.service.PillarAllocationCalculator.Report;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PLANEJAMENTO §5 R2, R9, R10 — the pillar allocation math. Pure unit tests: no Spring, no database.
 *
 * <p>The {@link Canonical} scenario is the one the E3 gate checks by hand against a spreadsheet, so
 * every number in it is deliberately round.
 */
class PillarAllocationCalculatorTest {

    /** The fixed MVP targets (WorkspaceDefaultsSeeder): sum to 100. */
    private static final Map<Pillar, Integer> TARGETS = Map.of(
            FIXED_COSTS, 30, COMFORT, 10, GOALS, 20, PLEASURES, 10, FINANCIAL_FREEDOM, 25, KNOWLEDGE, 5);

    private static ExpenseEntry expense(Pillar pillar, long amountCents) {
        return new ExpenseEntry(pillar, amountCents, false);
    }

    private static ExpenseEntry systemExpense(Pillar pillar, long amountCents) {
        return new ExpenseEntry(pillar, amountCents, true);
    }

    private static PillarLine line(Report report, Pillar pillar) {
        return report.pillars().stream()
                .filter(l -> l.pillar() == pillar)
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("R10 — percent is spent ÷ month income × 100")
    class Rule10 {

        @Test
        void percentIsSpentOverIncome() {
            Report report = PillarAllocationCalculator.allocate(
                    500_000L, List.of(expense(FIXED_COSTS, 150_000L)), List.of(), TARGETS);

            assertThat(report.noIncome()).isFalse();
            assertThat(line(report, FIXED_COSTS).spentCents()).isEqualTo(150_000L);
            assertThat(line(report, FIXED_COSTS).actualPercent()).isEqualTo(30.0);
        }

        @Test
        void spendingCanExceedItsTarget() {
            // PLEASURES target is 10%; spending 20% of income overshoots it — the dashboard flags this.
            Report report = PillarAllocationCalculator.allocate(
                    500_000L, List.of(expense(PLEASURES, 100_000L)), List.of(), TARGETS);

            assertThat(line(report, PLEASURES).targetPercent()).isEqualTo(10);
            assertThat(line(report, PLEASURES).actualPercent()).isEqualTo(20.0);
        }

        @Test
        void everyPillarIsAlwaysPresentEvenWithNoSpending() {
            Report report = PillarAllocationCalculator.allocate(500_000L, List.of(), List.of(), TARGETS);

            assertThat(report.pillars()).extracting(PillarLine::pillar).containsExactly(Pillar.values());
            assertThat(report.pillars()).allSatisfy(l -> {
                assertThat(l.spentCents()).isZero();
                assertThat(l.actualPercent()).isEqualTo(0.0);
            });
        }
    }

    @Nested
    @DisplayName("R2 — the statement-payment debit is excluded")
    class Rule2 {

        @Test
        void aSystemCategoryExpenseIsNotCounted() {
            // The card purchase already counted the spend; the payment debit under the system category
            // must not count it again, or the pillar would double.
            Report report = PillarAllocationCalculator.allocate(
                    500_000L,
                    List.of(expense(FIXED_COSTS, 150_000L), systemExpense(FIXED_COSTS, 200_000L)),
                    List.of(),
                    TARGETS);

            assertThat(line(report, FIXED_COSTS).spentCents()).isEqualTo(150_000L);
            assertThat(line(report, FIXED_COSTS).actualPercent()).isEqualTo(30.0);
        }
    }

    @Nested
    @DisplayName("R9 — contributions consume their pillar")
    class Rule9 {

        @Test
        void aContributionCountsTowardItsPillarAlongsideExpenses() {
            Report report = PillarAllocationCalculator.allocate(
                    500_000L,
                    List.of(expense(GOALS, 10_000L)),
                    List.of(new ContributionEntry(GOALS, 40_000L)),
                    TARGETS);

            // 10.000 expense + 40.000 contribution = 50.000 → 10% of 500.000
            assertThat(line(report, GOALS).spentCents()).isEqualTo(50_000L);
            assertThat(line(report, GOALS).actualPercent()).isEqualTo(10.0);
        }

        @Test
        void financialFreedomFillsOnlyThroughContributions() {
            Report report = PillarAllocationCalculator.allocate(
                    500_000L, List.of(), List.of(new ContributionEntry(FINANCIAL_FREEDOM, 125_000L)), TARGETS);

            assertThat(line(report, FINANCIAL_FREEDOM).spentCents()).isEqualTo(125_000L);
            assertThat(line(report, FINANCIAL_FREEDOM).actualPercent()).isEqualTo(25.0);
        }
    }

    @Nested
    @DisplayName("no income — percents are null, absolutes kept (R10)")
    class NoIncome {

        @Test
        void zeroIncomeYieldsNullPercentsAndKeepsAbsolutes() {
            Report report = PillarAllocationCalculator.allocate(
                    0L,
                    List.of(expense(FIXED_COSTS, 80_000L)),
                    List.of(new ContributionEntry(GOALS, 20_000L)),
                    TARGETS);

            assertThat(report.noIncome()).isTrue();
            assertThat(report.incomeCents()).isZero();
            assertThat(report.pillars()).allSatisfy(l -> assertThat(l.actualPercent()).isNull());
            assertThat(line(report, FIXED_COSTS).spentCents()).isEqualTo(80_000L);
            assertThat(line(report, GOALS).spentCents()).isEqualTo(20_000L);
        }
    }

    /**
     * The canonical scenario the E3 gate verifies by hand.
     *
     * <p>Income R$ 5.000,00 (500.000 cents). Spending by competency: a FIXED_COSTS card purchase of
     * R$ 1.500,00, a PLEASURES card purchase of R$ 500,00, a direct PLEASURES expense of R$ 250,00,
     * a direct KNOWLEDGE expense of R$ 100,00. Contributions: GOALS R$ 400,00, FINANCIAL_FREEDOM
     * R$ 1.000,00. Excluded: a statement-payment debit of R$ 2.000,00 under the system category (R2)
     * and a plain R$ 2.000,00 transfer between checking accounts (R4, never reaches the calculator).
     *
     * <p>Expected: FIXED_COSTS 150.000 = 30%, COMFORT 0 = 0%, GOALS 40.000 = 8%, PLEASURES 75.000 =
     * 15%, FINANCIAL_FREEDOM 100.000 = 20%, KNOWLEDGE 10.000 = 2%.
     */
    @Nested
    @DisplayName("canonical scenario (E3 gate)")
    class Canonical {

        @Test
        void percentsMatchTheHandComputation() {
            long income = 500_000L;
            List<ExpenseEntry> expenses = List.of(
                    expense(FIXED_COSTS, 150_000L), // card purchase
                    expense(PLEASURES, 50_000L), // card purchase
                    expense(PLEASURES, 25_000L), // direct expense
                    expense(KNOWLEDGE, 10_000L), // direct expense
                    systemExpense(FIXED_COSTS, 200_000L)); // statement payment — excluded (R2)
            List<ContributionEntry> contributions = List.of(
                    new ContributionEntry(GOALS, 40_000L), new ContributionEntry(FINANCIAL_FREEDOM, 100_000L));

            Report report = PillarAllocationCalculator.allocate(income, expenses, contributions, TARGETS);

            assertThat(report.noIncome()).isFalse();
            assertThat(report.incomeCents()).isEqualTo(500_000L);

            assertThat(line(report, FIXED_COSTS).spentCents()).isEqualTo(150_000L);
            assertThat(line(report, FIXED_COSTS).actualPercent()).isEqualTo(30.0);

            assertThat(line(report, COMFORT).spentCents()).isZero();
            assertThat(line(report, COMFORT).actualPercent()).isEqualTo(0.0);

            assertThat(line(report, GOALS).spentCents()).isEqualTo(40_000L);
            assertThat(line(report, GOALS).actualPercent()).isEqualTo(8.0);

            assertThat(line(report, PLEASURES).spentCents()).isEqualTo(75_000L);
            assertThat(line(report, PLEASURES).actualPercent()).isEqualTo(15.0);

            assertThat(line(report, FINANCIAL_FREEDOM).spentCents()).isEqualTo(100_000L);
            assertThat(line(report, FINANCIAL_FREEDOM).actualPercent()).isEqualTo(20.0);

            assertThat(line(report, KNOWLEDGE).spentCents()).isEqualTo(10_000L);
            assertThat(line(report, KNOWLEDGE).actualPercent()).isEqualTo(2.0);
        }
    }
}
