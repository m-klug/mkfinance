package br.com.mkfinance.recurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.domain.model.RecurrenceFrequency;
import br.com.mkfinance.domain.service.RecurrenceScheduler;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PLANEJAMENTO §7 (Fase 2) — turning a recurrence's explicit schedule into occurrence dates. Pure
 * unit tests: no Spring, no database, no clock (BE-7 aceite).
 */
class RecurrenceSchedulerTest {

    @Nested
    @DisplayName("MONTHLY")
    class Monthly {

        @Test
        @DisplayName("a normal day of month occurs once, on that day")
        void normalDay() {
            List<LocalDate> dates = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.MONTHLY,
                    10,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    YearMonth.of(2026, 7));

            assertThat(dates).containsExactly(LocalDate.of(2026, 7, 10));
        }

        @Test
        @DisplayName("day 31 in February falls on the 28th (common year) instead of skipping the month")
        void day31InFebruaryCommonYear() {
            List<LocalDate> dates = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.MONTHLY,
                    31,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    YearMonth.of(2027, 2));

            assertThat(dates).containsExactly(LocalDate.of(2027, 2, 28));
        }

        @Test
        @DisplayName("day 31 in February of a leap year falls on the 29th")
        void day31InFebruaryLeapYear() {
            List<LocalDate> dates = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.MONTHLY,
                    31,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    YearMonth.of(2028, 2));

            assertThat(dates).containsExactly(LocalDate.of(2028, 2, 29));
        }

        @Test
        @DisplayName("day 31 in a 30-day month falls on the 30th")
        void day31InAThirtyDayMonth() {
            List<LocalDate> dates = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.MONTHLY,
                    31,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    YearMonth.of(2026, 4));

            assertThat(dates).containsExactly(LocalDate.of(2026, 4, 30));
        }

        @Test
        @DisplayName("a month entirely before startDate produces nothing")
        void beforeStartDate() {
            List<LocalDate> dates = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.MONTHLY,
                    10,
                    null,
                    null,
                    LocalDate.of(2026, 8, 1),
                    null,
                    YearMonth.of(2026, 7));

            assertThat(dates).isEmpty();
        }

        @Test
        @DisplayName("a month entirely after endDate produces nothing")
        void afterEndDate() {
            List<LocalDate> dates = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.MONTHLY,
                    10,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 6, 30),
                    YearMonth.of(2026, 7));

            assertThat(dates).isEmpty();
        }

        @Test
        @DisplayName("the occurrence is included when it lands exactly on startDate or endDate")
        void inclusiveBoundaries() {
            List<LocalDate> onStart = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.MONTHLY,
                    10,
                    null,
                    null,
                    LocalDate.of(2026, 7, 10),
                    null,
                    YearMonth.of(2026, 7));
            List<LocalDate> onEnd = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.MONTHLY,
                    10,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 7, 10),
                    YearMonth.of(2026, 7));

            assertThat(onStart).containsExactly(LocalDate.of(2026, 7, 10));
            assertThat(onEnd).containsExactly(LocalDate.of(2026, 7, 10));
        }

        @Test
        void rejectsMissingDayOfMonth() {
            assertThatThrownBy(() -> RecurrenceScheduler.occurrencesIn(
                            RecurrenceFrequency.MONTHLY,
                            null,
                            null,
                            null,
                            LocalDate.of(2026, 1, 1),
                            null,
                            YearMonth.of(2026, 7)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("WEEKLY")
    class Weekly {

        @Test
        @DisplayName("every matching weekday of the month is an occurrence")
        void everyMatchingWeekdayInMonth() {
            // July 2026: Fridays fall on the 3rd, 10th, 17th, 24th and 31st.
            List<LocalDate> dates = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.WEEKLY,
                    null,
                    DayOfWeek.FRIDAY,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    YearMonth.of(2026, 7));

            assertThat(dates)
                    .containsExactly(
                            LocalDate.of(2026, 7, 3),
                            LocalDate.of(2026, 7, 10),
                            LocalDate.of(2026, 7, 17),
                            LocalDate.of(2026, 7, 24),
                            LocalDate.of(2026, 7, 31));
        }

        @Test
        @DisplayName("crossing the month boundary: each month is computed independently, no gaps or dupes")
        void crossingMonthBoundary() {
            List<LocalDate> july = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.WEEKLY,
                    null,
                    DayOfWeek.FRIDAY,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    YearMonth.of(2026, 7));
            List<LocalDate> august = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.WEEKLY,
                    null,
                    DayOfWeek.FRIDAY,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    YearMonth.of(2026, 8));

            assertThat(july).last().isEqualTo(LocalDate.of(2026, 7, 31));
            assertThat(august).first().isEqualTo(LocalDate.of(2026, 8, 7));
            // No date appears in both months, and the gap between the last of July and the first of
            // August is exactly one week.
            assertThat(august.get(0).minusWeeks(1)).isEqualTo(july.get(july.size() - 1));
        }

        @Test
        @DisplayName("startDate mid-month excludes earlier occurrences of the same weekday")
        void startDateMidMonth() {
            List<LocalDate> dates = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.WEEKLY,
                    null,
                    DayOfWeek.FRIDAY,
                    null,
                    LocalDate.of(2026, 7, 15),
                    null,
                    YearMonth.of(2026, 7));

            assertThat(dates)
                    .containsExactly(LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 24), LocalDate.of(2026, 7, 31));
        }

        @Test
        void rejectsMissingDayOfWeek() {
            assertThatThrownBy(() -> RecurrenceScheduler.occurrencesIn(
                            RecurrenceFrequency.WEEKLY,
                            null,
                            null,
                            null,
                            LocalDate.of(2026, 1, 1),
                            null,
                            YearMonth.of(2026, 7)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("YEARLY")
    class Yearly {

        @Test
        @DisplayName("occurs once, in the matching month/day, every year")
        void occursInMatchingMonth() {
            List<LocalDate> dates = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.YEARLY,
                    15,
                    null,
                    3,
                    LocalDate.of(2020, 1, 1),
                    null,
                    YearMonth.of(2026, 3));

            assertThat(dates).containsExactly(LocalDate.of(2026, 3, 15));
        }

        @Test
        @DisplayName("every other month of the year produces nothing")
        void otherMonthsProduceNothing() {
            List<LocalDate> dates = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.YEARLY,
                    15,
                    null,
                    3,
                    LocalDate.of(2020, 1, 1),
                    null,
                    YearMonth.of(2026, 4));

            assertThat(dates).isEmpty();
        }

        @Test
        @DisplayName("February 29 anniversary clamps to the 28th outside leap years")
        void leapDayAnniversaryClampsInCommonYears() {
            List<LocalDate> commonYear = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.YEARLY,
                    29,
                    null,
                    2,
                    LocalDate.of(2024, 2, 29),
                    null,
                    YearMonth.of(2026, 2));
            List<LocalDate> leapYear = RecurrenceScheduler.occurrencesIn(
                    RecurrenceFrequency.YEARLY,
                    29,
                    null,
                    2,
                    LocalDate.of(2024, 2, 29),
                    null,
                    YearMonth.of(2028, 2));

            assertThat(commonYear).containsExactly(LocalDate.of(2026, 2, 28));
            assertThat(leapYear).containsExactly(LocalDate.of(2028, 2, 29));
        }

        @Test
        void rejectsMissingMonthOfYear() {
            assertThatThrownBy(() -> RecurrenceScheduler.occurrencesIn(
                            RecurrenceFrequency.YEARLY,
                            15,
                            null,
                            null,
                            LocalDate.of(2026, 1, 1),
                            null,
                            YearMonth.of(2026, 3)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsMissingDayOfMonth() {
            assertThatThrownBy(() -> RecurrenceScheduler.occurrencesIn(
                            RecurrenceFrequency.YEARLY,
                            null,
                            null,
                            3,
                            LocalDate.of(2026, 1, 1),
                            null,
                            YearMonth.of(2026, 3)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
