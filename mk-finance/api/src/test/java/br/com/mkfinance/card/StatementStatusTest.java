package br.com.mkfinance.card;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.application.card.StatementTotals;
import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.domain.model.StatementStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The statement lifecycle: what is stored (how much was paid) versus what is derived (whether the
 * closing date has passed). Pure unit tests — this is exactly the logic that would otherwise need a
 * scheduled job to flip statuses at midnight.
 */
class StatementStatusTest {

    private static Statement statementClosingOn(LocalDate closingDate, StatementStatus status) {
        return Statement.builder()
                .referenceMonth(closingDate.withDayOfMonth(1))
                .closingDate(closingDate)
                .dueDate(closingDate.plusMonths(1))
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("CLOSED is derived from the calendar, never stored")
    class DerivedClosed {

        private final LocalDate closing = LocalDate.of(2026, 7, 20);

        @Test
        void openBeforeTheClosingDate() {
            Statement statement = statementClosingOn(closing, StatementStatus.OPEN);
            assertThat(statement.effectiveStatus(LocalDate.of(2026, 7, 19))).isEqualTo(StatementStatus.OPEN);
        }

        @Test
        @DisplayName("still OPEN on the closing day itself — purchases that day belong to it (R3)")
        void openOnTheClosingDate() {
            Statement statement = statementClosingOn(closing, StatementStatus.OPEN);
            assertThat(statement.effectiveStatus(closing)).isEqualTo(StatementStatus.OPEN);
        }

        @Test
        void closedTheDayAfter() {
            Statement statement = statementClosingOn(closing, StatementStatus.OPEN);
            assertThat(statement.effectiveStatus(LocalDate.of(2026, 7, 21))).isEqualTo(StatementStatus.CLOSED);
        }

        @Test
        @DisplayName("a payment status outranks the calendar: PARTIAL stays PARTIAL after closing")
        void paymentStatusWinsOverClosing() {
            assertThat(statementClosingOn(closing, StatementStatus.PARTIAL)
                            .effectiveStatus(LocalDate.of(2026, 8, 30)))
                    .isEqualTo(StatementStatus.PARTIAL);
            assertThat(statementClosingOn(closing, StatementStatus.PAID)
                            .effectiveStatus(LocalDate.of(2026, 8, 30)))
                    .isEqualTo(StatementStatus.PAID);
        }
    }

    @Nested
    @DisplayName("immutability (R6)")
    class Immutability {

        @Test
        void onlyAFullyPaidStatementIsFrozen() {
            LocalDate closing = LocalDate.of(2026, 7, 20);
            assertThat(statementClosingOn(closing, StatementStatus.PAID).isImmutable()).isTrue();
            assertThat(statementClosingOn(closing, StatementStatus.PARTIAL).isImmutable()).isFalse();
            assertThat(statementClosingOn(closing, StatementStatus.OPEN).isImmutable()).isFalse();
        }
    }

    @Nested
    @DisplayName("payment status implied by the amounts")
    class ImpliedPaymentStatus {

        @Test
        void nothingPaidIsOpen() {
            assertThat(new StatementTotals(10_000L, 0L).paymentStatus()).isEqualTo(StatementStatus.OPEN);
        }

        @Test
        void someOfItIsPartial() {
            assertThat(new StatementTotals(10_000L, 4_000L).paymentStatus()).isEqualTo(StatementStatus.PARTIAL);
        }

        @Test
        void allOfItIsPaid() {
            assertThat(new StatementTotals(10_000L, 10_000L).paymentStatus()).isEqualTo(StatementStatus.PAID);
        }

        @Test
        @DisplayName("an empty statement stays OPEN rather than counting as paid")
        void emptyStatementIsOpen() {
            assertThat(StatementTotals.EMPTY.paymentStatus()).isEqualTo(StatementStatus.OPEN);
            assertThat(StatementTotals.EMPTY.remainingCents()).isZero();
        }

        @Test
        @DisplayName("remaining never goes negative — there is no credit balance in the MVP")
        void remainingIsNeverNegative() {
            assertThat(new StatementTotals(10_000L, 12_000L).remainingCents()).isZero();
        }
    }
}
