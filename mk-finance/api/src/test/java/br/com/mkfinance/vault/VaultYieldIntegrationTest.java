package br.com.mkfinance.vault;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.dashboard.DashboardSummaryResponse;
import br.com.mkfinance.api.dashboard.PillarAllocationLineResponse;
import br.com.mkfinance.api.dashboard.PillarAllocationReportResponse;
import br.com.mkfinance.api.transaction.TransactionRequest;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.api.transaction.TransferRequest;
import br.com.mkfinance.api.vault.VaultCompositionResponse;
import br.com.mkfinance.api.vault.VaultEntryResponse;
import br.com.mkfinance.api.vault.VaultResponse;
import br.com.mkfinance.api.vault.VaultYieldRequest;
import br.com.mkfinance.api.vault.VaultYieldResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.VaultEntrySource;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * BE-11 end to end — DESENVOLVIMENTO-CAIXINHAS.md §4.5, roteiro §8 step 10: the whole
 * {@code createVaultYield} flow driven through the real HTTP + security stack, exactly what a
 * client sees. {@link VaultContributionIntegrationTest} and {@link
 * br.com.mkfinance.dashboard.DashboardIntegrationTest} stay untouched — this class is the proof
 * that booking a yield does not disturb either of them.
 */
class VaultYieldIntegrationTest extends AbstractVaultIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    private AccountResponse checking;
    private AccountResponse brokerage;

    @BeforeEach
    void setUp() {
        register();
        checking = createAccount("Conta Corrente", AccountType.CHECKING, 2_000_000L);
        brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
    }

    private TransactionResponse aporte(UUID toVaultId, long amountCents) {
        return session.post(
                        path("/transfers"),
                        new TransferRequest(checking.id(), brokerage.id(), amountCents, DATE, "Aporte", null, null, toVaultId),
                        TransactionResponse.class)
                .getBody();
    }

    private VaultCompositionResponse composition(UUID accountId) {
        return session.get(path("/accounts/" + accountId + "/vaults"), VaultCompositionResponse.class)
                .getBody();
    }

    private long balanceOfVault(UUID accountId, UUID vaultId) {
        return composition(accountId).vaults().stream()
                .filter(v -> v.id().equals(vaultId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Vault " + vaultId + " not in composition"))
                .balanceCents();
    }

    private ResponseEntity<VaultYieldResponse> bookYield(UUID accountId, long amountCents, LocalDate date) {
        return session.post(
                path("/accounts/" + accountId + "/vault-yields"),
                new VaultYieldRequest(amountCents, date, null),
                VaultYieldResponse.class);
    }

    private java.util.List<VaultEntryResponse> entriesOf(UUID vaultId) {
        return Arrays.asList(
                session.get(path("/vaults/" + vaultId + "/entries"), VaultEntryResponse[].class).getBody());
    }

    private CategoryResponse incomeCategory() {
        CategoryResponse[] categories =
                session.get(path("/categories?type=INCOME"), CategoryResponse[].class).getBody();
        return Arrays.stream(categories)
                .filter(category -> !category.system())
                .findFirst()
                .orElseThrow();
    }

    private void income(AccountResponse account, long amountCents, LocalDate date) {
        session.post(
                path("/transactions"),
                new TransactionRequest(
                        account.id(), incomeCategory().id(), TransactionType.INCOME, amountCents, date, "salário", null,
                        null, null),
                TransactionResponse.class);
    }

    private DashboardSummaryResponse summary(String month) {
        return session.get(path("/dashboard/summary?month=" + month), DashboardSummaryResponse.class)
                .getBody();
    }

    private PillarAllocationReportResponse pillarAllocation(String month) {
        return session.get(path("/dashboard/pillar-allocation?month=" + month), PillarAllocationReportResponse.class)
                .getBody();
    }

    // --- roteiro §8, step 10: the canonical split ------------------------------------------------

    @Nested
    @DisplayName("the canonical scenario — bases 6.000/4.500, yield R$ 100,01")
    class CanonicalSplit {

        @Test
        @DisplayName("shares sum exactly to 10001 and the account balance rises by the same amount")
        void splitsExactlyAndRaisesTheAccountBalance() {
            VaultResponse reserva = createVault(brokerage.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            VaultResponse viagem = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);
            aporte(reserva.id(), 600_000L);
            aporte(viagem.id(), 450_000L);

            long balanceBefore = composition(brokerage.id()).balanceCents();

            ResponseEntity<VaultYieldResponse> response = bookYield(brokerage.id(), 10_001L, DATE);
            VaultYieldResponse yield = response.getBody();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(yield.amountCents()).isEqualTo(10_001L);
            assertThat(yield.shares()).hasSize(2);
            assertThat(yield.shares().stream().mapToLong(s -> s.amountCents()).sum()).isEqualTo(10_001L);
            assertThat(yield.shares())
                    .filteredOn(s -> s.vaultId().equals(reserva.id()))
                    .singleElement()
                    .satisfies(s -> assertThat(s.amountCents()).isEqualTo(5_715L));
            assertThat(yield.shares())
                    .filteredOn(s -> s.vaultId().equals(viagem.id()))
                    .singleElement()
                    .satisfies(s -> assertThat(s.amountCents()).isEqualTo(4_286L));

            assertThat(balanceOfVault(brokerage.id(), reserva.id())).isEqualTo(600_000L + 5_715L);
            assertThat(balanceOfVault(brokerage.id(), viagem.id())).isEqualTo(450_000L + 4_286L);
            assertThat(composition(brokerage.id()).balanceCents()).isEqualTo(balanceBefore + 10_001L);
        }

        @Test
        @DisplayName("DELETE /transactions/{id} cascades both ledger lines away, restoring the pre-yield balances")
        void deletingTheTransactionUndoesTheWholeSplit() {
            VaultResponse reserva = createVault(brokerage.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            VaultResponse viagem = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);
            aporte(reserva.id(), 600_000L);
            aporte(viagem.id(), 450_000L);
            VaultYieldResponse yield = bookYield(brokerage.id(), 10_001L, DATE).getBody();

            ResponseEntity<Void> deleted = session.delete(path("/transactions/" + yield.transactionId()), Void.class);

            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(balanceOfVault(brokerage.id(), reserva.id())).isEqualTo(600_000L);
            assertThat(balanceOfVault(brokerage.id(), viagem.id())).isEqualTo(450_000L);
            // The CONTRIBUTION lines from the aportes survive — only the YIELD lines are gone.
            assertThat(entriesOf(reserva.id())).extracting(VaultEntryResponse::source)
                    .containsExactly(VaultEntrySource.CONTRIBUTION);
            assertThat(entriesOf(viagem.id())).extracting(VaultEntryResponse::source)
                    .containsExactly(VaultEntrySource.CONTRIBUTION);
        }
    }

    // --- §4.5: no positive base leaves the whole yield unallocated -------------------------------

    @Nested
    @DisplayName("no vault holds a positive settled balance")
    class NoEligibleVault {

        @Test
        @DisplayName("an account with no vaults at all books the yield with an empty split")
        void accountWithNoVaultsGetsAnEmptySplit() {
            long balanceBefore = composition(brokerage.id()).balanceCents();

            VaultYieldResponse yield = bookYield(brokerage.id(), 5_000L, DATE).getBody();

            assertThat(yield.shares()).isEmpty();
            assertThat(composition(brokerage.id()).balanceCents()).isEqualTo(balanceBefore + 5_000L);
            assertThat(composition(brokerage.id()).unallocatedCents()).isEqualTo(balanceBefore + 5_000L);
        }

        @Test
        @DisplayName("every vault sitting at zero also gets an empty split — no invented allocation")
        void everyVaultAtZeroGetsAnEmptySplit() {
            VaultResponse reserva = createVault(brokerage.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);

            VaultYieldResponse yield = bookYield(brokerage.id(), 5_000L, DATE).getBody();

            assertThat(yield.shares()).isEmpty();
            assertThat(balanceOfVault(brokerage.id(), reserva.id())).isZero();
            assertThat(entriesOf(reserva.id())).isEmpty();
        }
    }

    // --- BE-11's own dashboard regression proof ---------------------------------------------------

    @Nested
    @DisplayName("dashboard regression — the yield never counts as income nor consumes a pillar")
    class DashboardRegression {

        @Test
        @DisplayName("the account balance rises but the income tile and the six pillar percentages do not move")
        void yieldRaisesBalanceOnlyNeverIncomeOrPillars() {
            VaultResponse reserva = createVault(brokerage.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            VaultResponse viagem = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);

            // A salary so the month has income to measure percentages against (R10), plus the two
            // aportes that consume GOALS/FINANCIAL_FREEDOM the ordinary way.
            income(checking, 1_000_000L, DATE);
            aporte(reserva.id(), 600_000L); // FINANCIAL_FREEDOM
            aporte(viagem.id(), 450_000L); // GOALS

            DashboardSummaryResponse summaryBefore = summary("2026-03");
            PillarAllocationReportResponse allocationBefore = pillarAllocation("2026-03");
            long accountBalanceBefore = composition(brokerage.id()).balanceCents();

            bookYield(brokerage.id(), 10_001L, DATE);

            DashboardSummaryResponse summaryAfter = summary("2026-03");
            PillarAllocationReportResponse allocationAfter = pillarAllocation("2026-03");

            // The one thing that must change: the account (and vault) balance.
            assertThat(composition(brokerage.id()).balanceCents()).isEqualTo(accountBalanceBefore + 10_001L);

            // Everything the dashboard reports about the month is untouched by the yield.
            assertThat(summaryAfter.incomeCents())
                    .as("the yield is a system-category INCOME entry — excluded from the income tile")
                    .isEqualTo(summaryBefore.incomeCents())
                    .isEqualTo(1_000_000L);
            assertThat(summaryAfter.totalBalanceWithInvestmentsCents())
                    .as("the balance including investments does move — the money really arrived")
                    .isEqualTo(summaryBefore.totalBalanceWithInvestmentsCents() + 10_001L);
            assertThat(allocationAfter.incomeCents()).isEqualTo(allocationBefore.incomeCents());

            assertThat(allocationAfter.pillars()).hasSameSizeAs(allocationBefore.pillars());
            for (Pillar pillar : Pillar.values()) {
                assertThat(lineOf(allocationAfter, pillar).spentCents())
                        .as("pillar %s spentCents unchanged by the yield", pillar)
                        .isEqualTo(lineOf(allocationBefore, pillar).spentCents());
                assertThat(lineOf(allocationAfter, pillar).actualPercent())
                        .as("pillar %s actualPercent unchanged by the yield", pillar)
                        .isEqualTo(lineOf(allocationBefore, pillar).actualPercent());
            }
        }

        private PillarAllocationLineResponse lineOf(PillarAllocationReportResponse report, Pillar pillar) {
            return report.pillars().stream().filter(p -> p.pillar() == pillar).findFirst().orElseThrow();
        }
    }
}
