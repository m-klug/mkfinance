package br.com.mkfinance.vault;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.vault.VaultCompositionResponse;
import br.com.mkfinance.api.vault.VaultRequest;
import br.com.mkfinance.api.vault.VaultResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransferDirection;
import br.com.mkfinance.domain.model.VaultGoalStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** BE-9 aceite — CRUD, composition identity, goal, archive/delete guards (contract tag {@code vaults}). */
class VaultIntegrationTest extends AbstractVaultIntegrationTest {

    @BeforeEach
    void setUp() {
        register();
    }

    @Nested
    @DisplayName("CRUD")
    class Crud {

        @Test
        void createsListsUpdatesAndDeletesAVault() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);

            ResponseEntity<VaultResponse> created = session.post(
                    path("/accounts/" + account.id() + "/vaults"),
                    new VaultRequest("Reserva", ContributionPillar.FINANCIAL_FREEDOM, null, null, null),
                    VaultResponse.class);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            VaultResponse vault = created.getBody();
            assertThat(vault.name()).isEqualTo("Reserva");
            assertThat(vault.archived()).isFalse();
            assertThat(vault.balanceCents()).isZero();

            VaultCompositionResponse composition = session.get(
                            path("/accounts/" + account.id() + "/vaults"), VaultCompositionResponse.class)
                    .getBody();
            assertThat(composition.vaults()).extracting(VaultResponse::id).containsExactly(vault.id());

            ResponseEntity<VaultResponse> renamed = session.patch(
                    path("/vaults/" + vault.id()),
                    new VaultRequest("Reserva de emergência", ContributionPillar.FINANCIAL_FREEDOM, null, null, null),
                    VaultResponse.class);
            assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(renamed.getBody().name()).isEqualTo("Reserva de emergência");

            ResponseEntity<Void> deleted = session.delete(path("/vaults/" + vault.id()), Void.class);
            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            VaultCompositionResponse afterDelete = session.get(
                            path("/accounts/" + account.id() + "/vaults"), VaultCompositionResponse.class)
                    .getBody();
            assertThat(afterDelete.vaults()).isEmpty();
        }

        @Test
        void onlyInvestmentAndSavingsAccountsTakeVaults() {
            AccountResponse checking = createAccount("Conta corrente", AccountType.CHECKING, 0L);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/accounts/" + checking.id() + "/vaults"),
                    new VaultRequest("Reserva", ContributionPillar.GOALS, null, null, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        void savingsAccountsTakeVaultsToo() {
            AccountResponse savings = createAccount("Poupança", AccountType.SAVINGS, 0L);

            ResponseEntity<VaultResponse> response = session.post(
                    path("/accounts/" + savings.id() + "/vaults"),
                    new VaultRequest("Reserva", ContributionPillar.GOALS, null, null, null),
                    VaultResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        void namesAreUniquePerAccountCaseInsensitively() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/accounts/" + account.id() + "/vaults"),
                    new VaultRequest("RESERVA", ContributionPillar.GOALS, null, null, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("VAULT_NAME_DUPLICATE");
        }

        @Test
        void theSameNameIsAllowedOnADifferentAccount() {
            AccountResponse corretora = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            AccountResponse poupanca = createAccount("Poupança", AccountType.SAVINGS, 0L);
            createVault(corretora.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);

            ResponseEntity<VaultResponse> response = session.post(
                    path("/accounts/" + poupanca.id() + "/vaults"),
                    new VaultRequest("Reserva", ContributionPillar.FINANCIAL_FREEDOM, null, null, null),
                    VaultResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        void aTargetDateWithoutATargetAmountIsValidationError() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/accounts/" + account.id() + "/vaults"),
                    new VaultRequest("Viagem", ContributionPillar.GOALS, null, LocalDate.of(2027, 1, 1), null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        void unknownVaultIs404() {
            UUID unknown = UUID.randomUUID();

            ResponseEntity<ErrorResponse> response = session.patch(
                    path("/vaults/" + unknown),
                    new VaultRequest("Qualquer", ContributionPillar.GOALS, null, null, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("VAULT_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("hasVaults (BE-9's exception to file ownership)")
    class HasVaults {

        @Test
        void anAccountWithNoVaultsReportsFalse() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            assertThat(account.hasVaults()).isFalse();
        }

        @Test
        void theFirstNonArchivedVaultFlipsItToTrue() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);

            AccountResponse[] accounts = session.get(path("/accounts"), AccountResponse[].class).getBody();
            AccountResponse refreshed = java.util.Arrays.stream(accounts)
                    .filter(a -> a.id().equals(account.id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(refreshed.hasVaults()).isTrue();
        }
    }

    @Nested
    @DisplayName("composition identity — Σ vaults + unallocated = account balance (§4.2/§4.3)")
    class CompositionIdentity {

        @Test
        void settledPendingAndUnallocatedAllAddUp() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 1_000_000L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            VaultResponse viagem = createVault(account.id(), "Viagem", ContributionPillar.GOALS);

            // Settled contribution into Reserva.
            manualContribution(account.id(), reserva.id(), TransferDirection.IN, 600_000L, TransactionStatus.SETTLED);
            // Pending contribution into Viagem — must not move balanceCents, only projectedBalanceCents.
            manualContribution(account.id(), viagem.id(), TransferDirection.IN, 100_000L, TransactionStatus.PENDING);

            VaultCompositionResponse composition = session.get(
                            path("/accounts/" + account.id() + "/vaults"), VaultCompositionResponse.class)
                    .getBody();

            assertThat(composition.balanceCents()).isEqualTo(1_600_000L);
            assertThat(composition.projectedBalanceCents()).isEqualTo(1_700_000L);
            assertThat(composition.allocatedCents()).isEqualTo(600_000L); // settled only
            assertThat(composition.unallocatedCents()).isEqualTo(1_000_000L);
            assertThat(composition.projectedUnallocatedCents()).isEqualTo(1_000_000L);
            // The identity itself, at both levels.
            assertThat(composition.allocatedCents() + composition.unallocatedCents())
                    .isEqualTo(composition.balanceCents());

            VaultResponse reservaInComposition = composition.vaults().stream()
                    .filter(v -> v.id().equals(reserva.id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(reservaInComposition.balanceCents()).isEqualTo(600_000L);
            assertThat(reservaInComposition.projectedBalanceCents()).isEqualTo(600_000L);

            VaultResponse viagemInComposition = composition.vaults().stream()
                    .filter(v -> v.id().equals(viagem.id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(viagemInComposition.balanceCents()).isZero();
            assertThat(viagemInComposition.projectedBalanceCents()).isEqualTo(100_000L);
        }

        @Test
        void withdrawalReducesTheVaultButNotTheOtherOnes() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            manualContribution(account.id(), reserva.id(), TransferDirection.IN, 600_000L, TransactionStatus.SETTLED);

            manualContribution(account.id(), reserva.id(), TransferDirection.OUT, 200_000L, TransactionStatus.SETTLED);

            VaultResponse refreshed = session.get(
                            path("/accounts/" + account.id() + "/vaults"), VaultCompositionResponse.class)
                    .getBody()
                    .vaults()
                    .get(0);
            assertThat(refreshed.balanceCents()).isEqualTo(400_000L);
        }
    }

    @Nested
    @DisplayName("goal (§4.6)")
    class Goal {

        @Test
        void goalIsPresentExactlyWhenATargetAmountIsSet() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse withGoal = createVaultWithGoal(
                    account.id(), "Viagem", ContributionPillar.GOALS, 1_200_000L, LocalDate.of(2027, 7, 31));
            VaultResponse withoutGoal = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);

            assertThat(withGoal.goal()).isNotNull();
            assertThat(withGoal.goal().targetAmountCents()).isEqualTo(1_200_000L);
            assertThat(withGoal.goal().status()).isEqualTo(VaultGoalStatus.IN_PROGRESS);
            assertThat(withoutGoal.goal()).isNull();
        }

        @Test
        void progressReflectsTheVaultsSettledBalance() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse viagem = createVaultWithGoal(
                    account.id(), "Viagem", ContributionPillar.GOALS, 1_200_000L, LocalDate.of(2027, 7, 31));
            manualContribution(account.id(), viagem.id(), TransferDirection.IN, 450_000L, TransactionStatus.SETTLED);

            VaultResponse refreshed = session.get(
                            path("/accounts/" + account.id() + "/vaults"), VaultCompositionResponse.class)
                    .getBody()
                    .vaults()
                    .get(0);

            assertThat(refreshed.goal().remainingCents()).isEqualTo(750_000L);
            assertThat(refreshed.goal().progressPercent()).isEqualTo(37.5);
        }
    }

    @Nested
    @DisplayName("archive and delete (§4.7)")
    class ArchiveAndDelete {

        @Test
        void archivingARequiresBothBalancesToBeZero() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            manualContribution(account.id(), reserva.id(), TransferDirection.IN, 600_000L, TransactionStatus.SETTLED);

            ResponseEntity<ErrorResponse> blocked =
                    session.post(path("/vaults/" + reserva.id() + "/archive"), null, ErrorResponse.class);
            assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(blocked.getBody().code()).isEqualTo("VAULT_NOT_EMPTY");

            manualContribution(account.id(), reserva.id(), TransferDirection.OUT, 600_000L, TransactionStatus.SETTLED);

            ResponseEntity<VaultResponse> archived =
                    session.post(path("/vaults/" + reserva.id() + "/archive"), null, VaultResponse.class);
            assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(archived.getBody().archived()).isTrue();
        }

        @Test
        void deletingAVaultWithLedgerHistoryIsRefused() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse viagem = createVault(account.id(), "Viagem", ContributionPillar.GOALS);
            manualContribution(account.id(), viagem.id(), TransferDirection.IN, 50_000L, TransactionStatus.SETTLED);
            manualContribution(account.id(), viagem.id(), TransferDirection.OUT, 50_000L, TransactionStatus.SETTLED);

            ResponseEntity<ErrorResponse> response =
                    session.delete(path("/vaults/" + viagem.id()), ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("VAULT_HAS_ENTRIES");
        }

        @Test
        void archivingTheLastVaultReturnsTheAccountToNoVaultBehaviour() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);

            session.post(path("/vaults/" + reserva.id() + "/archive"), null, VaultResponse.class);

            AccountResponse[] accounts = session.get(path("/accounts"), AccountResponse[].class).getBody();
            AccountResponse refreshed = java.util.Arrays.stream(accounts)
                    .filter(a -> a.id().equals(account.id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(refreshed.hasVaults()).isFalse();
        }
    }

    @Nested
    @DisplayName("insufficient balance guard (§4.3)")
    class InsufficientBalance {

        @Test
        void aWithdrawalThatWouldOverdrawTheVaultIsRefused() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            manualContribution(account.id(), reserva.id(), TransferDirection.IN, 600_000L, TransactionStatus.SETTLED);

            // Called directly against the service layer (no /transactions endpoint yet — that is
            // BE-10), so the failure surfaces as AppException itself rather than the HTTP ErrorResponse
            // GlobalExceptionHandler would translate it into.
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> manualContribution(
                            account.id(), reserva.id(), TransferDirection.OUT, 800_000L, TransactionStatus.SETTLED))
                    .isInstanceOf(br.com.mkfinance.domain.exception.AppException.class)
                    .satisfies(ex -> {
                        br.com.mkfinance.domain.exception.AppException app =
                                (br.com.mkfinance.domain.exception.AppException) ex;
                        assertThat(app.getCode()).isEqualTo("VAULT_INSUFFICIENT_BALANCE");
                        // 8.000 tirados de 6.000 faltam 2.000 — positivo, e a diferença, não o saldo
                        // que sobrou (que seria negativo e tornaria a mensagem do cliente absurda).
                        // O cliente conhece o valor que enviou, então daqui ele deriva tanto
                        // "faltam R$ 2.000" quanto "só há R$ 6.000 disponíveis".
                        assertThat(app.getParams()).containsEntry("shortfallCents", 200_000L);
                    });

            VaultResponse refreshed = session.get(
                            path("/accounts/" + account.id() + "/vaults"), VaultCompositionResponse.class)
                    .getBody()
                    .vaults()
                    .stream()
                    .filter(v -> v.id().equals(reserva.id()))
                    .findFirst()
                    .orElseThrow();
            // The overdrawing withdrawal was refused (thrown, transaction rolled back inside
            // VaultLinkService's own @Transactional), so the vault still holds its original balance.
            assertThat(refreshed.balanceCents()).isEqualTo(600_000L);
        }
    }
}
