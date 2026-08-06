package br.com.mkfinance.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.domain.service.YieldDistributor;
import br.com.mkfinance.domain.service.YieldDistributor.Share;
import br.com.mkfinance.domain.service.YieldDistributor.VaultBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * DESENVOLVIMENTO-CAIXINHAS.md §4.5 — pure unit tests, no Spring, no database. Every test that cares
 * about which vault an id belongs to picks fixed {@link UUID}s so the largest-remainder tie-break
 * order is reproducible instead of depending on {@link UUID#randomUUID()}'s luck.
 */
class YieldDistributorTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @Nested
    @DisplayName("the canonical scenario (roteiro §8, step 10)")
    class Canonical {

        @Test
        @DisplayName("R$ 100,01 split over bases 6.000/4.500 → 57,15 + 42,86, summing exactly to 10001")
        void indivisibleAmountSumsExactly() {
            List<Share> shares = YieldDistributor.distribute(
                    10_001L,
                    List.of(
                            new VaultBase(A, "Reserva", 600_000L),
                            new VaultBase(B, "Viagem", 450_000L)));

            assertThat(shares).containsExactlyInAnyOrder(new Share(A, 5_715L), new Share(B, 4_286L));
            assertThat(shares.stream().mapToLong(Share::amountCents).sum()).isEqualTo(10_001L);
        }
    }

    @Nested
    @DisplayName("exact division leaves no leftover to place")
    class ExactDivision {

        @Test
        void proportionalSplitWithNoRemainder() {
            List<Share> shares = YieldDistributor.distribute(
                    10_000L,
                    List.of(new VaultBase(A, "Reserva", 600_000L), new VaultBase(B, "Viagem", 400_000L)));

            assertThat(shares).containsExactlyInAnyOrder(new Share(A, 6_000L), new Share(B, 4_000L));
        }
    }

    @Nested
    @DisplayName("ties in the largest-remainder assignment")
    class Ties {

        @Test
        @DisplayName("a genuine remainder tie (different bases, same remainder) desempata by larger base")
        void tiedRemaindersBreakByLargerBase() {
            // Solved by construction: base A=3, base B=1, sum=4, total=2 gives
            // floor(2*3/4)=1 rem 2, and floor(2*1/4)=0 rem 2 — the remainders are exactly equal
            // (both 2) despite the bases differing, so this is a real tie, not a coincidence of
            // proportional ordering. The single leftover cent (2 - 1 = 1) must go to the larger base.
            List<Share> shares = YieldDistributor.distribute(
                    2L, List.of(new VaultBase(A, "Zulu", 3L), new VaultBase(B, "Alfa", 1L)));

            // B's floor and leftover both landed on zero, so it is dropped from the result entirely
            // (amount_cents <> 0, V19) — A alone carries the whole yield.
            assertThat(shares).containsExactly(new Share(A, 2L));
        }

        @Test
        @DisplayName("equal base and equal remainder desempatam by name, case-insensitively")
        void identicalBaseBreaksByNameCaseInsensitive() {
            List<Share> shares = YieldDistributor.distribute(
                    1L,
                    List.of(new VaultBase(A, "zulu", 100_000L), new VaultBase(B, "Alfa", 100_000L)));

            // Both bases (and hence both remainders) are identical, so the single leftover cent must
            // go to "Alfa" (lexicographically first, case-insensitively) over "zulu" — which is left
            // with a zero share and therefore dropped from the result.
            assertThat(shares).containsExactly(new Share(B, 1L));
        }

        @Test
        @DisplayName("equal base and equal name desempatam by id")
        void identicalBaseAndNameBreaksById() {
            UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID higher = UUID.fromString("00000000-0000-0000-0000-000000000002");

            List<Share> shares = YieldDistributor.distribute(
                    1L,
                    List.of(new VaultBase(higher, "Reserva", 100_000L), new VaultBase(lower, "Reserva", 100_000L)));

            assertThat(shares).containsExactly(new Share(lower, 1L));
        }
    }

    @Nested
    @DisplayName("degenerate bases")
    class DegenerateBases {

        @Test
        @DisplayName("Σbase == 0 (every vault at zero) returns an empty split, not an invented allocation")
        void allZeroBasesReturnEmpty() {
            List<Share> shares = YieldDistributor.distribute(
                    10_000L, List.of(new VaultBase(A, "Reserva", 0L), new VaultBase(B, "Viagem", 0L)));

            assertThat(shares).isEmpty();
        }

        @Test
        @DisplayName("no vaults at all returns an empty split")
        void noVaultsReturnEmpty() {
            assertThat(YieldDistributor.distribute(10_000L, List.of())).isEmpty();
        }

        @Test
        @DisplayName("a vault sitting on a negative settled balance is excluded, not clamped to zero")
        void negativeBaseVaultIsExcluded() {
            List<Share> shares = YieldDistributor.distribute(
                    10_000L,
                    List.of(new VaultBase(A, "Reserva", 600_000L), new VaultBase(B, "A Descoberto", -50_000L)));

            assertThat(shares).containsExactly(new Share(A, 10_000L));
        }

        @Test
        @DisplayName("every vault negative or zero, mixed, still returns empty")
        void mixOfZeroAndNegativeReturnsEmpty() {
            List<Share> shares = YieldDistributor.distribute(
                    5_000L, List.of(new VaultBase(A, "Zero", 0L), new VaultBase(B, "Negativa", -1L)));

            assertThat(shares).isEmpty();
        }
    }

    @Nested
    @DisplayName("a single eligible vault takes the whole yield")
    class SingleVault {

        @Test
        void oneVaultGetsEverything() {
            List<Share> shares = YieldDistributor.distribute(12_345L, List.of(new VaultBase(A, "Reserva", 999L)));

            assertThat(shares).containsExactly(new Share(A, 12_345L));
        }

        @Test
        @DisplayName("a single eligible vault among excluded ones still gets everything")
        void oneEligibleAmongExcludedGetsEverything() {
            List<Share> shares = YieldDistributor.distribute(
                    12_345L,
                    List.of(new VaultBase(A, "Reserva", 999L), new VaultBase(B, "Zerada", 0L)));

            assertThat(shares).containsExactly(new Share(A, 12_345L));
        }
    }

    @Nested
    @DisplayName("the shares always reconstruct the total")
    class SumInvariant {

        @Test
        @DisplayName("holds across a sweep of totals and base pairs, including badly-dividing ones")
        void sumMatchesAcrossASweep() {
            for (long total = 1L; total <= 500L; total++) {
                List<Share> shares = YieldDistributor.distribute(
                        total,
                        List.of(
                                new VaultBase(A, "Reserva", 333L),
                                new VaultBase(B, "Viagem", 667L),
                                new VaultBase(C, "Aposentadoria", 1L)));
                assertThat(shares.stream().mapToLong(Share::amountCents).sum())
                        .as("total preserved for %d cents", total)
                        .isEqualTo(total);
            }
        }

        @Test
        @DisplayName("holds for very large amounts without overflowing long arithmetic")
        void sumMatchesForLargeAmounts() {
            long total = Long.MAX_VALUE / 4;
            List<Share> shares = YieldDistributor.distribute(
                    total,
                    List.of(
                            new VaultBase(A, "Reserva", Long.MAX_VALUE / 3),
                            new VaultBase(B, "Viagem", Long.MAX_VALUE / 5)));

            assertThat(shares.stream().mapToLong(Share::amountCents).sum()).isEqualTo(total);
        }
    }

    @Nested
    @DisplayName("rejected input")
    class RejectedInput {

        @Test
        void nonPositiveTotalIsRejected() {
            assertThatThrownBy(() -> YieldDistributor.distribute(0L, List.of(new VaultBase(A, "Reserva", 1L))))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> YieldDistributor.distribute(-1L, List.of(new VaultBase(A, "Reserva", 1L))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
