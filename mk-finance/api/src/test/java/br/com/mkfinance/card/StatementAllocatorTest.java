package br.com.mkfinance.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.domain.service.StatementAllocator;
import br.com.mkfinance.domain.service.StatementAllocator.StatementSchedule;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PLANEJAMENTO §5 R3 — which statement a purchase lands on, and where its installments go from
 * there. Pure unit tests: no Spring, no database, no clock.
 */
class StatementAllocatorTest {

    @Nested
    @DisplayName("the closing-day boundary")
    class ClosingDayBoundary {

        @Test
        @DisplayName("before closing → current statement")
        void beforeClosing() {
            assertThat(StatementAllocator.firstReferenceMonth(LocalDate.of(2026, 7, 14), 20))
                    .isEqualTo(YearMonth.of(2026, 7));
        }

        @Test
        @DisplayName("ON the closing day → still the current statement (the boundary is inclusive)")
        void onClosingDay() {
            assertThat(StatementAllocator.firstReferenceMonth(LocalDate.of(2026, 7, 20), 20))
                    .isEqualTo(YearMonth.of(2026, 7));
        }

        @Test
        @DisplayName("the day after closing → the next statement")
        void dayAfterClosing() {
            assertThat(StatementAllocator.firstReferenceMonth(LocalDate.of(2026, 7, 21), 20))
                    .isEqualTo(YearMonth.of(2026, 8));
        }

        @Test
        @DisplayName("first day of the month is always inside the current statement")
        void firstDayOfMonth() {
            assertThat(StatementAllocator.firstReferenceMonth(LocalDate.of(2026, 7, 1), 1))
                    .isEqualTo(YearMonth.of(2026, 7));
        }

        @Test
        @DisplayName("buying in December after closing rolls into January of the next year")
        void afterClosingInDecemberRollsTheYear() {
            assertThat(StatementAllocator.firstReferenceMonth(LocalDate.of(2026, 12, 29), 28))
                    .isEqualTo(YearMonth.of(2027, 1));
        }

        @ParameterizedTest(name = "bought {0} with closing on day {1} → {2}")
        @CsvSource({
            "2026-02-27, 28, 2026-02",
            "2026-02-28, 28, 2026-02",
            "2027-02-28, 28, 2027-02",
            "2028-02-28, 28, 2028-02",
            "2028-02-29, 28, 2028-03",
            "2026-01-31, 28, 2026-02",
            "2026-03-29, 28, 2026-04"
        })
        @DisplayName("closing on the 28th behaves in short months, leap year included")
        void closingDay28InShortMonths(LocalDate purchaseDate, int closingDay, YearMonth expected) {
            assertThat(StatementAllocator.firstReferenceMonth(purchaseDate, closingDay)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("installments land on consecutive statements")
    class ConsecutiveStatements {

        @Test
        void outrightPurchaseOccupiesOneStatement() {
            assertThat(StatementAllocator.referenceMonths(LocalDate.of(2026, 7, 10), 20, 1))
                    .containsExactly(YearMonth.of(2026, 7));
        }

        @Test
        @DisplayName("3 installments bought on the closing day → July, August, September")
        void threeInstallmentsFromTheClosingDay() {
            assertThat(StatementAllocator.referenceMonths(LocalDate.of(2026, 7, 20), 20, 3))
                    .containsExactly(YearMonth.of(2026, 7), YearMonth.of(2026, 8), YearMonth.of(2026, 9));
        }

        @Test
        @DisplayName("12 installments crossing the turn of the year")
        void twelveInstallmentsCrossingTheYear() {
            List<YearMonth> months = StatementAllocator.referenceMonths(LocalDate.of(2026, 8, 25), 20, 12);

            assertThat(months).hasSize(12);
            // Bought after the 20th, so it starts on September and runs to August of the next year.
            assertThat(months.get(0)).isEqualTo(YearMonth.of(2026, 9));
            assertThat(months.get(3)).isEqualTo(YearMonth.of(2026, 12));
            assertThat(months.get(4)).isEqualTo(YearMonth.of(2027, 1));
            assertThat(months.get(11)).isEqualTo(YearMonth.of(2027, 8));
        }

        @Test
        @DisplayName("60 installments — the contract's ceiling — stay consecutive over five years")
        void sixtyInstallmentsStayConsecutive() {
            List<YearMonth> months = StatementAllocator.referenceMonths(LocalDate.of(2026, 1, 5), 10, 60);

            assertThat(months).hasSize(60);
            assertThat(months.get(0)).isEqualTo(YearMonth.of(2026, 1));
            assertThat(months.get(59)).isEqualTo(YearMonth.of(2030, 12));
            for (int i = 1; i < months.size(); i++) {
                assertThat(months.get(i))
                        .as("installment %d follows the previous month", i + 1)
                        .isEqualTo(months.get(i - 1).plusMonths(1));
            }
        }

        @Test
        void rejectsNonPositiveInstallmentCount() {
            assertThatThrownBy(() -> StatementAllocator.referenceMonths(LocalDate.of(2026, 7, 1), 20, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("closing and due dates of a statement")
    class Schedule {

        @Test
        @DisplayName("due after closing in the same month when dueDay > closingDay")
        void dueLaterInTheSameMonth() {
            StatementSchedule schedule = StatementAllocator.scheduleFor(YearMonth.of(2026, 7), 10, 20);

            assertThat(schedule.closingDate()).isEqualTo(LocalDate.of(2026, 7, 10));
            assertThat(schedule.dueDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        }

        @Test
        @DisplayName("closing on the 28th, due on the 5th → due in the following month")
        void dueRollsIntoTheNextMonth() {
            StatementSchedule schedule = StatementAllocator.scheduleFor(YearMonth.of(2026, 7), 28, 5);

            assertThat(schedule.closingDate()).isEqualTo(LocalDate.of(2026, 7, 28));
            assertThat(schedule.dueDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        }

        @Test
        @DisplayName("equal closing and due days put the due date a month out, never on closing day")
        void equalDaysRollToTheNextMonth() {
            StatementSchedule schedule = StatementAllocator.scheduleFor(YearMonth.of(2026, 7), 15, 15);

            assertThat(schedule.dueDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        }

        @Test
        @DisplayName("February closes on the 28th like any other month")
        void februaryClosesOnDay28() {
            StatementSchedule schedule = StatementAllocator.scheduleFor(YearMonth.of(2027, 2), 28, 10);

            assertThat(schedule.closingDate()).isEqualTo(LocalDate.of(2027, 2, 28));
            assertThat(schedule.dueDate()).isEqualTo(LocalDate.of(2027, 3, 10));
        }

        @Test
        @DisplayName("a December statement is due in January of the next year")
        void decemberStatementIsDueNextYear() {
            StatementSchedule schedule = StatementAllocator.scheduleFor(YearMonth.of(2026, 12), 28, 5);

            assertThat(schedule.closingDate()).isEqualTo(LocalDate.of(2026, 12, 28));
            assertThat(schedule.dueDate()).isEqualTo(LocalDate.of(2027, 1, 5));
        }

        @Test
        @DisplayName("the due date is always strictly after the closing date, for every day pair")
        void dueIsAlwaysAfterClosing() {
            for (int closingDay = 1; closingDay <= 28; closingDay++) {
                for (int dueDay = 1; dueDay <= 28; dueDay++) {
                    StatementSchedule schedule =
                            StatementAllocator.scheduleFor(YearMonth.of(2027, 2), closingDay, dueDay);
                    assertThat(schedule.dueDate())
                            .as("closing %d / due %d", closingDay, dueDay)
                            .isAfter(schedule.closingDate());
                }
            }
        }

        @Test
        @DisplayName("every month of a leap year and a common year produces a valid schedule")
        void everyMonthProducesValidDates() {
            for (int year : new int[] {2027, 2028}) {
                for (int month = 1; month <= 12; month++) {
                    YearMonth reference = YearMonth.of(year, month);
                    StatementSchedule schedule = StatementAllocator.scheduleFor(reference, 28, 5);
                    assertThat(schedule.referenceMonth()).isEqualTo(reference);
                    assertThat(YearMonth.from(schedule.closingDate())).isEqualTo(reference);
                    assertThat(schedule.dueDate()).isAfter(schedule.closingDate());
                }
            }
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 29, 31, -1})
        void rejectsDaysOutsideTheSupportedRange(int day) {
            assertThatThrownBy(() -> StatementAllocator.scheduleFor(YearMonth.of(2026, 7), day, 10))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StatementAllocator.scheduleFor(YearMonth.of(2026, 7), 10, day))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> StatementAllocator.firstReferenceMonth(LocalDate.of(2026, 7, 1), day))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
