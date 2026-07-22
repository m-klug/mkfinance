package br.com.mkfinance.transaction;

import static br.com.mkfinance.domain.model.TransactionStatus.PENDING;
import static br.com.mkfinance.domain.model.TransactionStatus.SETTLED;
import static br.com.mkfinance.domain.model.TransactionType.EXPENSE;
import static br.com.mkfinance.domain.model.TransactionType.INCOME;
import static br.com.mkfinance.domain.model.TransactionType.TRANSFER;
import static br.com.mkfinance.domain.model.TransferDirection.IN;
import static br.com.mkfinance.domain.model.TransferDirection.OUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
import br.com.mkfinance.domain.service.BalanceCalculator;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.domain.service.BalanceCalculator.Entry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * PLANEJAMENTO §5 R5 — current balance counts settled movements only, projected also counts
 * pending ones. Pure unit tests: no Spring, no database.
 */
class BalanceCalculatorTest {

    private static Entry entry(TransactionType type, TransactionStatus status, long amountCents) {
        return new Entry(type, status, null, amountCents);
    }

    private static Entry transfer(TransferDirection direction, TransactionStatus status, long amountCents) {
        return new Entry(TRANSFER, status, direction, amountCents);
    }

    @Nested
    @DisplayName("signed amount")
    class SignedAmount {

        @Test
        void incomeAddsAndExpenseSubtracts() {
            assertThat(BalanceCalculator.signedAmountCents(entry(INCOME, SETTLED, 10_000L))).isEqualTo(10_000L);
            assertThat(BalanceCalculator.signedAmountCents(entry(EXPENSE, SETTLED, 10_000L))).isEqualTo(-10_000L);
        }

        @Test
        void transferLegFollowsItsDirection() {
            assertThat(BalanceCalculator.signedAmountCents(transfer(IN, SETTLED, 7_500L))).isEqualTo(7_500L);
            assertThat(BalanceCalculator.signedAmountCents(transfer(OUT, SETTLED, 7_500L))).isEqualTo(-7_500L);
        }

        @Test
        void transferLegWithoutDirectionIsCorruptDataNotZero() {
            assertThatThrownBy(() -> BalanceCalculator.signedAmountCents(new Entry(TRANSFER, SETTLED, null, 100L)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("current vs projected (R5)")
    class CurrentVsProjected {

        @Test
        void noMovementsLeavesTheInitialBalance() {
            Balance balance = BalanceCalculator.balance(25_000L, List.of());
            assertThat(balance.currentCents()).isEqualTo(25_000L);
            assertThat(balance.projectedCents()).isEqualTo(25_000L);
        }

        @Test
        void currentIgnoresPendingWhileProjectedIncludesIt() {
            List<Entry> entries = List.of(
                    entry(INCOME, SETTLED, 500_000L), // salary received
                    entry(EXPENSE, SETTLED, 120_000L), // rent paid
                    entry(EXPENSE, PENDING, 30_000L)); // scheduled bill

            assertThat(BalanceCalculator.currentBalanceCents(10_000L, entries)).isEqualTo(390_000L);
            assertThat(BalanceCalculator.projectedBalanceCents(10_000L, entries)).isEqualTo(360_000L);
        }

        @Test
        void pendingIncomeRaisesOnlyTheProjectedBalance() {
            List<Entry> entries = List.of(entry(INCOME, PENDING, 200_000L));

            Balance balance = BalanceCalculator.balance(0L, entries);
            assertThat(balance.currentCents()).isZero();
            assertThat(balance.projectedCents()).isEqualTo(200_000L);
        }

        @Test
        void balanceMatchesTheTwoSingleValueHelpers() {
            List<Entry> entries = List.of(
                    entry(INCOME, SETTLED, 100_00L),
                    entry(EXPENSE, PENDING, 40_00L),
                    transfer(OUT, SETTLED, 25_00L),
                    transfer(IN, PENDING, 15_00L));

            Balance balance = BalanceCalculator.balance(1_000L, entries);
            assertThat(balance.currentCents()).isEqualTo(BalanceCalculator.currentBalanceCents(1_000L, entries));
            assertThat(balance.projectedCents()).isEqualTo(BalanceCalculator.projectedBalanceCents(1_000L, entries));
        }

        @Test
        void balanceGoesNegativeWhenSpendingExceedsWhatIsThere() {
            Balance balance = BalanceCalculator.balance(5_000L, List.of(entry(EXPENSE, SETTLED, 8_000L)));
            assertThat(balance.currentCents()).isEqualTo(-3_000L);
        }
    }

    @Nested
    @DisplayName("transfers move money without creating income or expense (R4)")
    class Transfers {

        @Test
        void theTwoLegsCancelOutAcrossAccounts() {
            long amount = 150_000L;
            Balance source = BalanceCalculator.balance(200_000L, List.of(transfer(OUT, SETTLED, amount)));
            Balance destination = BalanceCalculator.balance(0L, List.of(transfer(IN, SETTLED, amount)));

            assertThat(source.currentCents()).isEqualTo(50_000L);
            assertThat(destination.currentCents()).isEqualTo(150_000L);
            // The workspace total is untouched: money moved, nothing was earned or spent.
            assertThat(source.currentCents() + destination.currentCents()).isEqualTo(200_000L);
        }

        @Test
        void pendingTransferMovesOnlyTheProjectedBalanceOnBothSides() {
            Balance source = BalanceCalculator.balance(100_000L, List.of(transfer(OUT, PENDING, 40_000L)));
            Balance destination = BalanceCalculator.balance(0L, List.of(transfer(IN, PENDING, 40_000L)));

            assertThat(source.currentCents()).isEqualTo(100_000L);
            assertThat(source.projectedCents()).isEqualTo(60_000L);
            assertThat(destination.currentCents()).isZero();
            assertThat(destination.projectedCents()).isEqualTo(40_000L);
        }
    }

    @Nested
    @DisplayName("aggregated buckets")
    class AggregatedBuckets {

        @Test
        void oneBucketPerGroupGivesTheSameResultAsRowByRow() {
            List<Entry> rows = List.of(
                    entry(EXPENSE, SETTLED, 1_000L), entry(EXPENSE, SETTLED, 2_500L), entry(EXPENSE, SETTLED, 700L));
            List<Entry> aggregated = List.of(entry(EXPENSE, SETTLED, 4_200L));

            assertThat(BalanceCalculator.balance(0L, aggregated)).isEqualTo(BalanceCalculator.balance(0L, rows));
        }
    }
}
