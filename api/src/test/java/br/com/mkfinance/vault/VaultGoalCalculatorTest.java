package br.com.mkfinance.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.domain.model.VaultGoalStatus;
import br.com.mkfinance.domain.service.VaultGoalCalculator;
import br.com.mkfinance.domain.service.VaultGoalCalculator.Goal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** DESENVOLVIMENTO-CAIXINHAS.md §4.6 — pure unit tests, no Spring, no database, no clock. */
class VaultGoalCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 5);

    @Nested
    @DisplayName("no deadline — valid, nothing to project")
    class NoDeadline {

        @Test
        void inProgressHasNullMonthsAndMonthlyNeeded() {
            Goal goal = VaultGoalCalculator.calculate(1_200_000L, null, 450_000L, TODAY);

            assertThat(goal.remainingCents()).isEqualTo(750_000L);
            assertThat(goal.progressPercent()).isEqualTo(37.5);
            assertThat(goal.monthsRemaining()).isNull();
            assertThat(goal.monthlyNeededCents()).isNull();
            assertThat(goal.status()).isEqualTo(VaultGoalStatus.IN_PROGRESS);
        }

        @Test
        void reachedWithoutADeadlineIsStillReached() {
            Goal goal = VaultGoalCalculator.calculate(100_000L, null, 100_000L, TODAY);

            assertThat(goal.remainingCents()).isZero();
            assertThat(goal.status()).isEqualTo(VaultGoalStatus.REACHED);
            assertThat(goal.monthsRemaining()).isNull();
            assertThat(goal.monthlyNeededCents()).isNull();
        }

        @Test
        void overshootingIsNotCappedAt100Percent() {
            Goal goal = VaultGoalCalculator.calculate(100_000L, null, 150_000L, TODAY);

            assertThat(goal.progressPercent()).isEqualTo(150.0);
            assertThat(goal.status()).isEqualTo(VaultGoalStatus.REACHED);
        }
    }

    @Nested
    @DisplayName("with a deadline — counts the current competency (§4.6)")
    class WithDeadline {

        @Test
        void theWorkedExampleFromTheRoteiro() {
            // "Viagem 2027": 4.500/12.000 = 37,5%, "faltam R$ 625,00/mês" — target date such that
            // exactly 12 months remain, counting August 2026 itself.
            LocalDate targetDate = LocalDate.of(2027, 7, 31);
            Goal goal = VaultGoalCalculator.calculate(1_200_000L, targetDate, 450_000L, TODAY);

            assertThat(goal.remainingCents()).isEqualTo(750_000L);
            assertThat(goal.progressPercent()).isEqualTo(37.5);
            assertThat(goal.monthsRemaining()).isEqualTo(12);
            assertThat(goal.monthlyNeededCents()).isEqualTo(62_500L);
            assertThat(goal.status()).isEqualTo(VaultGoalStatus.IN_PROGRESS);
        }

        @Test
        void deadlineThisMonthCountsAsOneMonthRemaining() {
            Goal goal = VaultGoalCalculator.calculate(100_000L, LocalDate.of(2026, 8, 31), 0L, TODAY);

            assertThat(goal.monthsRemaining()).isEqualTo(1);
            assertThat(goal.monthlyNeededCents()).isEqualTo(100_000L);
        }

        @Test
        void monthlyNeededRoundsUpSoPayingItAlwaysGetsThere() {
            // 100 cents over 3 months would floor to 33/mo, falling one cent short at the end.
            Goal goal = VaultGoalCalculator.calculate(1_000L, LocalDate.of(2026, 10, 31), 900L, TODAY);

            assertThat(goal.remainingCents()).isEqualTo(100L);
            assertThat(goal.monthsRemaining()).isEqualTo(3);
            assertThat(goal.monthlyNeededCents()).isEqualTo(34L); // ceilDiv(100, 3)
        }

        @Test
        void overdueDeadlineDemandsTheWholeRemainderNow() {
            Goal goal = VaultGoalCalculator.calculate(1_200_000L, LocalDate.of(2026, 6, 30), 450_000L, TODAY);

            assertThat(goal.monthsRemaining()).isZero();
            assertThat(goal.monthlyNeededCents()).isEqualTo(750_000L);
            assertThat(goal.monthlyNeededCents()).isNotNull(); // never null just because it is overdue
            assertThat(goal.status()).isEqualTo(VaultGoalStatus.OVERDUE);
        }

        @Test
        void reachedBeforeAnOverdueDeadlineStaysReachedNotOverdue() {
            // Batida wins over vencida (§4.6) — the deadline passed, but the goal was already hit.
            Goal goal = VaultGoalCalculator.calculate(100_000L, LocalDate.of(2026, 1, 31), 100_000L, TODAY);

            assertThat(goal.status()).isEqualTo(VaultGoalStatus.REACHED);
            assertThat(goal.remainingCents()).isZero();
            assertThat(goal.monthlyNeededCents()).isZero();
        }

        @Test
        void reachedStopsAskingRegardlessOfDeadline() {
            Goal goal = VaultGoalCalculator.calculate(100_000L, LocalDate.of(2027, 1, 31), 120_000L, TODAY);

            assertThat(goal.status()).isEqualTo(VaultGoalStatus.REACHED);
            assertThat(goal.remainingCents()).isZero();
            assertThat(goal.monthlyNeededCents()).isZero();
            assertThat(goal.progressPercent()).isEqualTo(120.0); // still uncapped
        }
    }

    @Nested
    @DisplayName("invalid input")
    class Invalid {

        @Test
        void zeroOrNegativeTargetIsRejected() {
            assertThatThrownBy(() -> VaultGoalCalculator.calculate(0L, null, 0L, TODAY))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> VaultGoalCalculator.calculate(-100L, null, 0L, TODAY))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
