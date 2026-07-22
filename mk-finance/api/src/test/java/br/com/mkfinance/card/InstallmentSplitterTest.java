package br.com.mkfinance.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.domain.service.InstallmentSplitter;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * PLANEJAMENTO §5 R1 — installments are split in integer cents and the remainder lands on the last
 * one. Pure unit tests: no Spring, no database.
 */
class InstallmentSplitterTest {

    @Nested
    @DisplayName("remainder placement")
    class RemainderPlacement {

        @Test
        @DisplayName("R$ 100,00 in 3 → 33,33 + 33,33 + 33,34 (the canonical example)")
        void canonicalExample() {
            assertThat(InstallmentSplitter.split(10_000L, 3)).containsExactly(3_333L, 3_333L, 3_334L);
        }

        @Test
        void exactDivisionLeavesNoRemainder() {
            assertThat(InstallmentSplitter.split(9_000L, 3)).containsExactly(3_000L, 3_000L, 3_000L);
        }

        @Test
        void singleInstallmentTakesTheWholeAmount() {
            assertThat(InstallmentSplitter.split(12_345L, 1)).containsExactly(12_345L);
        }

        @Test
        @DisplayName("only the last installment differs — the earlier ones are all equal")
        void onlyTheLastOneAbsorbsTheRemainder() {
            long[] parts = InstallmentSplitter.split(10_000L, 7);
            assertThat(parts).hasSize(7);
            assertThat(java.util.Arrays.stream(parts, 0, 6).distinct().count()).isEqualTo(1L);
            assertThat(parts[6]).isGreaterThan(parts[0]);
        }

        @Test
        @DisplayName("R$ 0,02 in 3 → 0 + 0 + 2: the sum still holds, which is what matters")
        void amountSmallerThanInstallmentCount() {
            assertThat(InstallmentSplitter.split(2L, 3)).containsExactly(0L, 0L, 2L);
        }

        @Test
        @DisplayName("one cent in one installment — the smallest possible purchase")
        void singleCent() {
            assertThat(InstallmentSplitter.split(1L, 1)).containsExactly(1L);
        }
    }

    @Nested
    @DisplayName("the parts always reconstruct the total")
    class SumInvariant {

        @ParameterizedTest(name = "{0} cents in {1}")
        @CsvSource({
            "10000, 3",
            "10000, 7",
            "10000, 12",
            "99999, 60",
            "1, 1",
            "2, 3",
            "333, 3",
            "100000000, 13",
            "9223372036854775807, 3"
        })
        void sumMatchesTotal(long totalCents, int count) {
            assertThat(LongStream.of(InstallmentSplitter.split(totalCents, count)).sum())
                    .isEqualTo(totalCents);
        }

        @Test
        @DisplayName("holds for every count from 1 to 60 on an amount that divides badly")
        void sumMatchesForEveryInstallmentCountTheContractAllows() {
            long total = 99_997L;
            for (int count = 1; count <= 60; count++) {
                assertThat(LongStream.of(InstallmentSplitter.split(total, count)).sum())
                        .as("total preserved when split in %d", count)
                        .isEqualTo(total);
            }
        }

        @Test
        @DisplayName("holds across a sweep of amounts — no rounding drift anywhere")
        void sumMatchesAcrossAmountSweep() {
            for (long total = 1L; total <= 500L; total++) {
                for (int count = 1; count <= 12; count++) {
                    assertThat(LongStream.of(InstallmentSplitter.split(total, count)).sum())
                            .as("%d cents in %d", total, count)
                            .isEqualTo(total);
                }
            }
        }
    }

    @Nested
    @DisplayName("rejected input")
    class RejectedInput {

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -10_000L})
        void nonPositiveTotal(long totalCents) {
            assertThatThrownBy(() -> InstallmentSplitter.split(totalCents, 3))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void nonPositiveCount(int count) {
            assertThatThrownBy(() -> InstallmentSplitter.split(10_000L, count))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
