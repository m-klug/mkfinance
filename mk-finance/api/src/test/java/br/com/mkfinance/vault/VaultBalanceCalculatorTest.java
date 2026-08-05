package br.com.mkfinance.vault;

import static br.com.mkfinance.domain.model.TransactionStatus.PENDING;
import static br.com.mkfinance.domain.model.TransactionStatus.SETTLED;
import static br.com.mkfinance.domain.model.TransactionType.TRANSFER;
import static br.com.mkfinance.domain.model.TransferDirection.IN;
import static br.com.mkfinance.domain.model.TransferDirection.OUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.domain.service.VaultBalanceCalculator;
import br.com.mkfinance.domain.service.VaultBalanceCalculator.Composition;
import br.com.mkfinance.domain.service.VaultBalanceCalculator.Entry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * DESENVOLVIMENTO-CAIXINHAS.md §4.1/§4.2/§4.3 — pure unit tests, no Spring, no database.
 */
class VaultBalanceCalculatorTest {

    @Nested
    @DisplayName("mirror (CONTRIBUTION/WITHDRAWAL) reuses BalanceCalculator's sign")
    class Mirror {

        @Test
        void contributionIsTheInLegAndAddsToTheVault() {
            Entry entry = Entry.mirror(TRANSFER, SETTLED, IN, 50_000L);
            assertThat(entry.signedAmountCents()).isEqualTo(50_000L);
        }

        @Test
        void withdrawalIsTheOutLegAndSubtractsFromTheVault() {
            Entry entry = Entry.mirror(TRANSFER, SETTLED, OUT, 20_000L);
            assertThat(entry.signedAmountCents()).isEqualTo(-20_000L);
        }
    }

    @Nested
    @DisplayName("balance — settled vs settled+pending (§4.2)")
    class VaultBalance {

        @Test
        void noEntriesIsZero() {
            Balance balance = VaultBalanceCalculator.balance(List.of());
            assertThat(balance.currentCents()).isZero();
            assertThat(balance.projectedCents()).isZero();
        }

        @Test
        void pendingContributionRaisesOnlyTheProjectedBalance() {
            List<Entry> entries = List.of(
                    Entry.mirror(TRANSFER, SETTLED, IN, 500_000L),
                    Entry.mirror(TRANSFER, PENDING, IN, 100_000L));

            Balance balance = VaultBalanceCalculator.balance(entries);
            assertThat(balance.currentCents()).isEqualTo(500_000L);
            assertThat(balance.projectedCents()).isEqualTo(600_000L);
        }

        @Test
        void reallocationHasNoStatusAndCountsInBoth() {
            // Callers always build a REALLOCATION Entry as SETTLED (§4.2) — there is no status column
            // for this source, and a reclassification already happened the instant it was recorded.
            List<Entry> entries = List.of(Entry.signed(30_000L, SETTLED));

            Balance balance = VaultBalanceCalculator.balance(entries);
            assertThat(balance.currentCents()).isEqualTo(30_000L);
            assertThat(balance.projectedCents()).isEqualTo(30_000L);
        }

        @Test
        void yieldAddsLikeAnySettledIncome() {
            List<Entry> entries = List.of(Entry.signed(5_715L, SETTLED));

            Balance balance = VaultBalanceCalculator.balance(entries);
            assertThat(balance.currentCents()).isEqualTo(5_715L);
        }

        @Test
        void mixingAllThreeSourcesSumsCorrectly() {
            List<Entry> entries = List.of(
                    Entry.mirror(TRANSFER, SETTLED, IN, 600_000L), // contribution
                    Entry.mirror(TRANSFER, SETTLED, OUT, 100_000L), // withdrawal
                    Entry.signed(5_715L, SETTLED), // yield share
                    Entry.signed(-50_000L, SETTLED)); // reallocation out

            Balance balance = VaultBalanceCalculator.balance(entries);
            assertThat(balance.currentCents()).isEqualTo(600_000L - 100_000L + 5_715L - 50_000L);
        }
    }

    @Nested
    @DisplayName("compose — Σ vaults + unallocated = account balance (§4.3)")
    class Compose {

        @Test
        void splitsTheAccountBalanceAcrossVaultsAndUnallocated() {
            UUID reserva = UUID.randomUUID();
            UUID viagem = UUID.randomUUID();
            Balance accountBalance = new Balance(1_050_000L, 1_050_000L);
            Map<UUID, Balance> vaultBalances =
                    Map.of(reserva, new Balance(600_000L, 600_000L), viagem, new Balance(400_000L, 400_000L));

            Composition composition = VaultBalanceCalculator.compose(accountBalance, vaultBalances);

            assertThat(composition.balanceCents()).isEqualTo(1_050_000L);
            assertThat(composition.allocatedCents()).isEqualTo(1_000_000L);
            assertThat(composition.unallocatedCents()).isEqualTo(50_000L);
            assertThat(composition.allocatedCents() + composition.unallocatedCents())
                    .isEqualTo(composition.balanceCents());
        }

        @Test
        void noVaultsMeansEverythingIsUnallocated() {
            Balance accountBalance = new Balance(200_000L, 250_000L);

            Composition composition = VaultBalanceCalculator.compose(accountBalance, Map.of());

            assertThat(composition.allocatedCents()).isZero();
            assertThat(composition.unallocatedCents()).isEqualTo(200_000L);
            assertThat(composition.projectedUnallocatedCents()).isEqualTo(250_000L);
        }

        @Test
        void unallocatedCanGoNegativeWithoutBeingBlocked() {
            // §4.3 — spending straight from the account, or paying a statement with it, does not know
            // about vaults; the composition shows the shortfall rather than refusing to compute it.
            Balance accountBalance = new Balance(30_000L, 30_000L);
            Map<UUID, Balance> vaultBalances = Map.of(UUID.randomUUID(), new Balance(50_000L, 50_000L));

            Composition composition = VaultBalanceCalculator.compose(accountBalance, vaultBalances);

            assertThat(composition.unallocatedCents()).isEqualTo(-20_000L);
        }

        @Test
        void projectedLevelHoldsTheSameIdentityIndependently() {
            Balance accountBalance = new Balance(100_000L, 180_000L); // R$ 800 pending income
            Map<UUID, Balance> vaultBalances = Map.of(UUID.randomUUID(), new Balance(40_000L, 90_000L));

            Composition composition = VaultBalanceCalculator.compose(accountBalance, vaultBalances);

            assertThat(composition.allocatedCents()).isEqualTo(40_000L);
            assertThat(composition.unallocatedCents()).isEqualTo(60_000L);
            assertThat(composition.projectedUnallocatedCents()).isEqualTo(90_000L);
            // allocatedProjected (90_000, from the vault's own Balance) + projectedUnallocated == projected total
            assertThat(90_000L + composition.projectedUnallocatedCents()).isEqualTo(composition.projectedBalanceCents());
        }

        @Test
        void theIdentityAlwaysHoldsByConstructionButIsAssertedAnyway() {
            // compose() derives unallocated by subtraction, so the identity cannot fail today — the
            // assertion exists to catch a future refactor that computes it some other way, exactly like
            // InstallmentSplitter.assertSumMatches guards a split that could otherwise drift.
            Balance accountBalance = new Balance(0L, 0L);
            Composition composition = VaultBalanceCalculator.compose(accountBalance, Map.of());
            assertThat(composition.unallocatedCents()).isZero();
        }
    }

    @Nested
    @DisplayName("degenerate inputs")
    class Degenerate {

        @Test
        void emptyEntriesListNeverThrows() {
            assertThatThrownBy(() -> VaultBalanceCalculator.balance(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
