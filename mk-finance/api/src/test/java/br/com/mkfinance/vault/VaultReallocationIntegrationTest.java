package br.com.mkfinance.vault;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.vault.VaultCompositionResponse;
import br.com.mkfinance.api.vault.VaultReallocationRequest;
import br.com.mkfinance.api.vault.VaultReallocationResponse;
import br.com.mkfinance.api.vault.VaultResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransferDirection;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** BE-9 aceite — remanejo, including the null sides (DESENVOLVIMENTO-CAIXINHAS.md §4.7). */
class VaultReallocationIntegrationTest extends AbstractVaultIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 5);

    @BeforeEach
    void setUp() {
        register();
    }

    @Nested
    @DisplayName("either side may be null — the unallocated slice (§4.7's table)")
    class NullSides {

        @Test
        void allocatesPreExistingAccountMoneyIntoAVault() {
            // null -> A: how a person with pre-existing account money splits it into vaults.
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 1_000_000L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);

            ResponseEntity<VaultReallocationResponse> response = session.post(
                    path("/vaults/reallocations"),
                    new VaultReallocationRequest(null, reserva.id(), 600_000L, DATE, "Onboarding"),
                    VaultReallocationResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            VaultReallocationResponse body = response.getBody();
            assertThat(body.fromVaultId()).isNull();
            assertThat(body.toVaultId()).isEqualTo(reserva.id());
            assertThat(body.accountId()).isEqualTo(account.id());

            VaultCompositionResponse composition = session.get(
                            path("/accounts/" + account.id() + "/vaults"), VaultCompositionResponse.class)
                    .getBody();
            assertThat(composition.balanceCents()).isEqualTo(1_000_000L); // account balance untouched
            assertThat(composition.allocatedCents()).isEqualTo(600_000L);
            assertThat(composition.unallocatedCents()).isEqualTo(400_000L);
        }

        @Test
        void emptiesTheLastVaultBackToUnallocated() {
            // A -> null: how the last vault gets emptied before archiving.
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            manualContribution(account.id(), reserva.id(), TransferDirection.IN, 600_000L, TransactionStatus.SETTLED);

            ResponseEntity<VaultReallocationResponse> response = session.post(
                    path("/vaults/reallocations"),
                    new VaultReallocationRequest(reserva.id(), null, 600_000L, DATE, null),
                    VaultReallocationResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            ResponseEntity<VaultResponse> archived =
                    session.post(path("/vaults/" + reserva.id() + "/archive"), null, VaultResponse.class);
            assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void movesMoneyBetweenTwoVaultsOfTheSameAccount() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            VaultResponse viagem = createVault(account.id(), "Viagem", ContributionPillar.GOALS);
            manualContribution(account.id(), reserva.id(), TransferDirection.IN, 600_000L, TransactionStatus.SETTLED);

            session.post(
                    path("/vaults/reallocations"),
                    new VaultReallocationRequest(reserva.id(), viagem.id(), 200_000L, DATE, "Repasse"),
                    VaultReallocationResponse.class);

            VaultCompositionResponse composition = session.get(
                            path("/accounts/" + account.id() + "/vaults"), VaultCompositionResponse.class)
                    .getBody();
            VaultResponse refreshedReserva = composition.vaults().stream()
                    .filter(v -> v.id().equals(reserva.id()))
                    .findFirst()
                    .orElseThrow();
            VaultResponse refreshedViagem = composition.vaults().stream()
                    .filter(v -> v.id().equals(viagem.id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(refreshedReserva.balanceCents()).isEqualTo(400_000L);
            assertThat(refreshedViagem.balanceCents()).isEqualTo(200_000L);
            assertThat(composition.allocatedCents()).isEqualTo(600_000L); // no account money changed
        }

        @Test
        void bothSidesNullIsValidationError() {
            ResponseEntity<ErrorResponse> response = session.post(
                    path("/vaults/reallocations"),
                    new VaultReallocationRequest(null, null, 100_000L, DATE, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().fieldErrors()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("guards")
    class Guards {

        @Test
        void differentAccountsIsRejected() {
            AccountResponse corretora = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            AccountResponse poupanca = createAccount("Poupança", AccountType.SAVINGS, 0L);
            VaultResponse reserva = createVault(corretora.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            VaultResponse cofre = createVault(poupanca.id(), "Cofre", ContributionPillar.FINANCIAL_FREEDOM);
            manualContribution(corretora.id(), reserva.id(), TransferDirection.IN, 600_000L, TransactionStatus.SETTLED);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/vaults/reallocations"),
                    new VaultReallocationRequest(reserva.id(), cofre.id(), 100_000L, DATE, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VAULT_ACCOUNT_MISMATCH");
        }

        @Test
        void anArchivedVaultIsRejected() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 500_000L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            session.post(path("/vaults/" + reserva.id() + "/archive"), null, VaultResponse.class);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/vaults/reallocations"),
                    new VaultReallocationRequest(null, reserva.id(), 100_000L, DATE, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("VAULT_ARCHIVED");
        }

        @Test
        void unknownVaultIs404() {
            java.util.UUID unknown = java.util.UUID.randomUUID();

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/vaults/reallocations"),
                    new VaultReallocationRequest(null, unknown, 100_000L, DATE, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("VAULT_NOT_FOUND");
        }

        @Test
        void movingMoreThanTheSourceHoldsIsRejected() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            VaultResponse viagem = createVault(account.id(), "Viagem", ContributionPillar.GOALS);
            manualContribution(account.id(), reserva.id(), TransferDirection.IN, 300_000L, TransactionStatus.SETTLED);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/vaults/reallocations"),
                    new VaultReallocationRequest(reserva.id(), viagem.id(), 500_000L, DATE, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("VAULT_INSUFFICIENT_BALANCE");

            // The refused reallocation must not have moved anything (rolled back).
            VaultCompositionResponse composition = session.get(
                            path("/accounts/" + account.id() + "/vaults"), VaultCompositionResponse.class)
                    .getBody();
            VaultResponse refreshedReserva = composition.vaults().stream()
                    .filter(v -> v.id().equals(reserva.id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(refreshedReserva.balanceCents()).isEqualTo(300_000L);
        }
    }

    @Nested
    @DisplayName("delete undoes both legs at once")
    class Delete {

        @Test
        void deletingARemovesBothSidesOfTheReallocation() {
            AccountResponse account = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse reserva = createVault(account.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            VaultResponse viagem = createVault(account.id(), "Viagem", ContributionPillar.GOALS);
            manualContribution(account.id(), reserva.id(), TransferDirection.IN, 600_000L, TransactionStatus.SETTLED);
            VaultReallocationResponse reallocation = session.post(
                            path("/vaults/reallocations"),
                            new VaultReallocationRequest(reserva.id(), viagem.id(), 200_000L, DATE, null),
                            VaultReallocationResponse.class)
                    .getBody();

            ResponseEntity<Void> deleted =
                    session.delete(path("/vaults/reallocations/" + reallocation.id()), Void.class);
            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            VaultCompositionResponse composition = session.get(
                            path("/accounts/" + account.id() + "/vaults"), VaultCompositionResponse.class)
                    .getBody();
            VaultResponse refreshedReserva = composition.vaults().stream()
                    .filter(v -> v.id().equals(reserva.id()))
                    .findFirst()
                    .orElseThrow();
            VaultResponse refreshedViagem = composition.vaults().stream()
                    .filter(v -> v.id().equals(viagem.id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(refreshedReserva.balanceCents()).isEqualTo(600_000L); // back to before
            assertThat(refreshedViagem.balanceCents()).isZero();
        }

        @Test
        void deletingAnUnknownReallocationIs404() {
            java.util.UUID unknown = java.util.UUID.randomUUID();

            ResponseEntity<ErrorResponse> response =
                    session.delete(path("/vaults/reallocations/" + unknown), ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("VAULT_REALLOCATION_NOT_FOUND");
        }
    }
}
