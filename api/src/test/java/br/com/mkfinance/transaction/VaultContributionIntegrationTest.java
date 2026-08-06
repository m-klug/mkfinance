package br.com.mkfinance.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.transaction.TransactionPageResponse;
import br.com.mkfinance.api.transaction.TransactionRequest;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.api.transaction.TransferRequest;
import br.com.mkfinance.api.transaction.TransferUpdateRequest;
import br.com.mkfinance.api.vault.VaultCompositionResponse;
import br.com.mkfinance.api.vault.VaultRequest;
import br.com.mkfinance.api.vault.VaultResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionStatus;
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
 * BE-10 — DESENVOLVIMENTO-CAIXINHAS.md §4.4: the pillar of a contribution ("aporte") moves from
 * the destination account's default to its vault's, once the account has one. Exercises the whole
 * resolution matrix (§4.4's table) plus the update/delete/settle paths that touch the vault ledger,
 * entirely through the real {@code /transfers}/{@code /transactions}/{@code /vaults} endpoints —
 * exactly what a client sees. {@link TransferIntegrationTest} stays untouched: it is the proof that
 * every account without a vault keeps working bit for bit as before this ficha.
 */
class VaultContributionIntegrationTest extends AbstractTransactionIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    private AccountResponse checking;

    @BeforeEach
    void setUp() {
        register();
        checking = createAccount("Conta Corrente", AccountType.CHECKING, 1_000_000L);
    }

    private VaultResponse createVault(UUID accountId, String name, ContributionPillar pillar) {
        return session.post(
                        path("/accounts/" + accountId + "/vaults"),
                        new VaultRequest(name, pillar, null, null, null),
                        VaultResponse.class)
                .getBody();
    }

    private void archive(UUID vaultId) {
        ResponseEntity<VaultResponse> response =
                session.post(path("/vaults/" + vaultId + "/archive"), null, VaultResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private TransactionResponse[] allTransactions() {
        return session.get(path("/transactions?size=200"), TransactionPageResponse.class)
                .getBody()
                .content()
                .toArray(new TransactionResponse[0]);
    }

    private TransactionResponse legOn(UUID accountId) {
        return Arrays.stream(allTransactions())
                .filter(transaction -> transaction.accountId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No transaction on account " + accountId));
    }

    private ResponseEntity<TransactionResponse> transfer(
            UUID fromAccountId, UUID toAccountId, long amountCents, ContributionPillar pillar, UUID fromVaultId, UUID toVaultId) {
        return session.post(
                path("/transfers"),
                new TransferRequest(fromAccountId, toAccountId, amountCents, DATE, "Aporte", pillar, fromVaultId, toVaultId),
                TransactionResponse.class);
    }

    // --- §4.4's matrix, row by row ---------------------------------------------------------------

    @Nested
    @DisplayName("row 1 — destination is not INVESTMENT/SAVINGS")
    class PlainDestination {

        @Test
        @DisplayName("an explicit pillar is still refused (unchanged from before BE-10)")
        void pillarIsRejected() {
            AccountResponse wallet = createAccount("Carteira", AccountType.CASH, 0L);
            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transfers"),
                    new TransferRequest(
                            checking.id(), wallet.id(), 10_000L, DATE, "Saque", ContributionPillar.GOALS, null, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("INVALID_CONTRIBUTION_PILLAR");
        }

        @Test
        @DisplayName("a vaultId sent to a plain destination is VAULT_NOT_FOUND")
        void vaultIdIsRejected() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Reserva", ContributionPillar.GOALS);
            AccountResponse wallet = createAccount("Carteira", AccountType.CASH, 0L);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transfers"),
                    new TransferRequest(
                            checking.id(), wallet.id(), 10_000L, DATE, "Saque", null, null, vault.id()),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("VAULT_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("rows 2-3 — INVESTMENT/SAVINGS with no active vault")
    class NoActiveVault {

        @Test
        @DisplayName("vaultId omitted keeps resolving from the account's default (comportamento de hoje)")
        void omittedFallsBackToAccountDefault() {
            AccountResponse brokerage =
                    createAccount("Corretora", AccountType.INVESTMENT, 0L, ContributionPillar.FINANCIAL_FREEDOM);

            TransactionResponse outgoing =
                    transfer(checking.id(), brokerage.id(), 50_000L, null, null, null).getBody();

            assertThat(outgoing.contributionPillar()).isEqualTo(ContributionPillar.FINANCIAL_FREEDOM);
        }

        @Test
        @DisplayName("a vaultId sent when the account has none at all is VAULT_NOT_FOUND")
        void vaultIdOnAccountWithNoVaultsIsNotFound() {
            AccountResponse brokerage =
                    createAccount("Corretora", AccountType.INVESTMENT, 0L, ContributionPillar.GOALS);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transfers"),
                    new TransferRequest(
                            checking.id(), brokerage.id(), 50_000L, DATE, "Aporte", null, null, UUID.randomUUID()),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("VAULT_NOT_FOUND");
        }

        @Test
        @DisplayName("once the only vault is archived, the account is back to comportamento de hoje")
        void onlyArchivedVaultFallsBackToAccountDefault() {
            AccountResponse brokerage =
                    createAccount("Corretora", AccountType.INVESTMENT, 0L, ContributionPillar.FINANCIAL_FREEDOM);
            VaultResponse vault = createVault(brokerage.id(), "Antiga", ContributionPillar.GOALS);
            archive(vault.id());

            TransactionResponse outgoing =
                    transfer(checking.id(), brokerage.id(), 50_000L, null, null, null).getBody();

            assertThat(outgoing.contributionPillar())
                    .as("hasVaults is false once every vault is archived (§4.7)")
                    .isEqualTo(ContributionPillar.FINANCIAL_FREEDOM);
        }

        @Test
        @DisplayName("naming that archived vault explicitly is still VAULT_NOT_FOUND, not VAULT_ARCHIVED")
        void namingTheOnlyArchivedVaultIsNotFound() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Antiga", ContributionPillar.GOALS);
            archive(vault.id());

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transfers"),
                    new TransferRequest(
                            checking.id(), brokerage.id(), 50_000L, DATE, "Aporte", null, null, vault.id()),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("VAULT_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("rows 4-8 — INVESTMENT/SAVINGS with an active vault")
    class ActiveVault {

        @Test
        @DisplayName("vaultId omitted is VAULT_REQUIRED")
        void omittedIsRequired() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            createVault(brokerage.id(), "Reserva", ContributionPillar.GOALS);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transfers"),
                    new TransferRequest(checking.id(), brokerage.id(), 50_000L, DATE, "Aporte", null, null, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VAULT_REQUIRED");
        }

        @Test
        @DisplayName("naming another (archived) vault of the same account is VAULT_ARCHIVED")
        void namingAnArchivedVaultIsArchived() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            createVault(brokerage.id(), "Ativa", ContributionPillar.GOALS);
            VaultResponse archived = createVault(brokerage.id(), "Arquivada", ContributionPillar.GOALS);
            archive(archived.id());

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transfers"),
                    new TransferRequest(
                            checking.id(), brokerage.id(), 50_000L, DATE, "Aporte", null, null, archived.id()),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("VAULT_ARCHIVED");
        }

        @Test
        @DisplayName("no explicit pillar: the vault's own pillar is used")
        void noPillarUsesVaultPillar() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);

            TransactionResponse outgoing =
                    transfer(checking.id(), brokerage.id(), 50_000L, null, null, vault.id()).getBody();

            assertThat(outgoing.contributionPillar()).isEqualTo(ContributionPillar.GOALS);
            assertThat(legOn(brokerage.id()).vaultId()).isEqualTo(vault.id());
            assertThat(balanceOfVault(brokerage.id(), vault.id())).isEqualTo(50_000L);
        }

        @Test
        @DisplayName("a pillar equal to the vault's own is accepted — round-tripping the read value must not fail")
        void matchingPillarIsAccepted() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);

            TransactionResponse outgoing = transfer(
                            checking.id(), brokerage.id(), 50_000L, ContributionPillar.GOALS, null, vault.id())
                    .getBody();

            assertThat(outgoing.contributionPillar()).isEqualTo(ContributionPillar.GOALS);
        }

        @Test
        @DisplayName("a pillar disagreeing with the vault's own is VAULT_PILLAR_CONFLICT")
        void conflictingPillarIsRejected() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transfers"),
                    new TransferRequest(
                            checking.id(),
                            brokerage.id(),
                            50_000L,
                            DATE,
                            "Aporte",
                            ContributionPillar.FINANCIAL_FREEDOM,
                            null,
                            vault.id()),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VAULT_PILLAR_CONFLICT");
        }
    }

    // --- Withdrawal / resgate (§4.4's origin symmetry, §4.8) -------------------------------------

    @Nested
    @DisplayName("resgate — the source leg is inspected too")
    class Withdrawal {

        @Test
        @DisplayName("a withdrawal from an account with an active vault requires fromVaultId")
        void requiresVaultWhenSourceHasOne() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Reserva", ContributionPillar.GOALS);
            transfer(checking.id(), brokerage.id(), 100_000L, null, null, vault.id());

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transfers"),
                    new TransferRequest(
                            brokerage.id(), checking.id(), 30_000L, DATE, "Resgate", null, null, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VAULT_REQUIRED");
        }

        @Test
        @DisplayName("a withdrawal never carries a pillar, even into another investment account (§4.8)")
        void withdrawalNeverCarriesPillar() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Reserva", ContributionPillar.GOALS);
            transfer(checking.id(), brokerage.id(), 100_000L, null, null, vault.id());

            AccountResponse otherBrokerage =
                    createAccount("Outra Corretora", AccountType.INVESTMENT, 0L, ContributionPillar.FINANCIAL_FREEDOM);
            TransactionResponse outgoing = session.post(
                            path("/transfers"),
                            new TransferRequest(
                                    brokerage.id(),
                                    otherBrokerage.id(),
                                    30_000L,
                                    DATE,
                                    "Resgate",
                                    null,
                                    vault.id(),
                                    null),
                            TransactionResponse.class)
                    .getBody();

            assertThat(outgoing.contributionPillar())
                    .as("the withdrawal leg itself never carries a pillar")
                    .isNull();
            assertThat(balanceOfVault(brokerage.id(), vault.id())).isEqualTo(70_000L);
        }

        @Test
        @DisplayName("withdrawing more than the vault holds is VAULT_INSUFFICIENT_BALANCE with the shortfall")
        void exceedingBalanceReportsShortfall() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Reserva", ContributionPillar.GOALS);
            transfer(checking.id(), brokerage.id(), 100_000L, null, null, vault.id());

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transfers"),
                    new TransferRequest(
                            brokerage.id(), checking.id(), 150_000L, DATE, "Resgate", null, vault.id(), null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("VAULT_INSUFFICIENT_BALANCE");
            assertThat(response.getBody().params().get("vaultId")).isEqualTo(vault.id().toString());
            assertThat(((Number) response.getBody().params().get("shortfallCents")).longValue()).isEqualTo(50_000L);
        }
    }

    // --- The extrato badge, batched --------------------------------------------------------------

    @Test
    @DisplayName("the extrato listing carries each leg's vaultId, batched")
    void listingCarriesVaultIdBatched() {
        AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
        VaultResponse vault = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);
        transfer(checking.id(), brokerage.id(), 40_000L, null, null, vault.id());

        assertThat(legOn(brokerage.id()).vaultId()).isEqualTo(vault.id());
        assertThat(legOn(checking.id()).vaultId()).as("checking has no vaults").isNull();
    }

    // --- PATCH /transfers re-pointing a vault (§4.4) ----------------------------------------------

    @Nested
    @DisplayName("update — vaultId re-points the addressed leg, symmetric to accountId")
    class Update {

        @Test
        @DisplayName("omitted vaultId carries the current link forward while the leg stays put")
        void omittedCarriesForward() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);
            transfer(checking.id(), brokerage.id(), 40_000L, null, null, vault.id());
            TransactionResponse incoming = legOn(brokerage.id());

            session.patch(
                    path("/transfers/" + incoming.id()),
                    new TransferUpdateRequest(null, 60_000L, DATE, "Aporte maior", null, null, null, null),
                    TransactionResponse.class);

            assertThat(legOn(brokerage.id()).vaultId()).isEqualTo(vault.id());
            assertThat(balanceOfVault(brokerage.id(), vault.id())).isEqualTo(60_000L);
        }

        @Test
        @DisplayName("an explicit vaultId re-targets the addressed leg to another vault of the same account")
        void explicitVaultIdRetargets() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse first = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);
            VaultResponse second = createVault(brokerage.id(), "Reserva", ContributionPillar.FINANCIAL_FREEDOM);
            transfer(checking.id(), brokerage.id(), 40_000L, null, null, first.id());
            TransactionResponse incoming = legOn(brokerage.id());

            TransactionResponse updated = session.patch(
                            path("/transfers/" + incoming.id()),
                            new TransferUpdateRequest(
                                    null, 40_000L, DATE, "Aporte", null, null, null, second.id()),
                            TransactionResponse.class)
                    .getBody();

            assertThat(updated.vaultId()).isEqualTo(second.id());
            assertThat(updated.contributionPillar())
                    .as("the pillar follows the vault it now points at")
                    .isEqualTo(ContributionPillar.FINANCIAL_FREEDOM);
            assertThat(balanceOfVault(brokerage.id(), first.id())).isZero();
            assertThat(balanceOfVault(brokerage.id(), second.id())).isEqualTo(40_000L);
        }

        @Test
        @DisplayName("moving the leg to another account with an active vault discards the old link and requires a new one")
        void movingAccountDiscardsOldVault() {
            AccountResponse firstBrokerage = createAccount("Corretora A", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(firstBrokerage.id(), "Viagem", ContributionPillar.GOALS);
            transfer(checking.id(), firstBrokerage.id(), 40_000L, null, null, vault.id());
            TransactionResponse incoming = legOn(firstBrokerage.id());

            AccountResponse secondBrokerage = createAccount("Corretora B", AccountType.INVESTMENT, 0L);
            createVault(secondBrokerage.id(), "Outra", ContributionPillar.GOALS);

            ResponseEntity<ErrorResponse> response = session.patch(
                    path("/transfers/" + incoming.id()),
                    new TransferUpdateRequest(
                            secondBrokerage.id(), 40_000L, DATE, "Aporte", null, null, null, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VAULT_REQUIRED");
        }

        @Test
        @DisplayName("moving the leg to a vault-less account drops the pillar and frees the old vault's balance")
        void movingAccountToPlainVaultLessAccountFreesTheVault() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);
            transfer(checking.id(), brokerage.id(), 40_000L, null, null, vault.id());
            TransactionResponse incoming = legOn(brokerage.id());

            AccountResponse wallet = createAccount("Carteira", AccountType.CASH, 0L);
            TransactionResponse moved = session.patch(
                            path("/transfers/" + incoming.id()),
                            new TransferUpdateRequest(wallet.id(), 40_000L, DATE, "Saque afinal", null, null, null, null),
                            TransactionResponse.class)
                    .getBody();

            assertThat(moved.contributionPillar()).isNull();
            assertThat(moved.vaultId()).isNull();
            assertThat(balanceOfVault(brokerage.id(), vault.id())).isZero();
        }
    }

    // --- delete / settle ---------------------------------------------------------------------------

    @Nested
    @DisplayName("delete unlinks the ledger before dropping the transaction (armadilha 6)")
    class Delete {

        @Test
        @DisplayName("deleting the contribution returns the vault to zero")
        void deletingReturnsVaultToZero() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Viagem", ContributionPillar.GOALS);
            TransactionResponse outgoing =
                    transfer(checking.id(), brokerage.id(), 40_000L, null, null, vault.id()).getBody();
            assertThat(balanceOfVault(brokerage.id(), vault.id())).isEqualTo(40_000L);

            ResponseEntity<Void> deleted = session.delete(path("/transactions/" + outgoing.id()), Void.class);

            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(balanceOfVault(brokerage.id(), vault.id())).isZero();
            // The vault itself survives — only its ledger entry went away — so it can be archived
            // cleanly, the DELETE-then-archive round trip §4.7 promises.
            archive(vault.id());
        }
    }

    @Nested
    @DisplayName("settle — a PENDING movement never overdraws by itself (§4.2/§4.3)")
    class Settle {

        @Test
        @DisplayName("settling a PENDING withdrawal that now exceeds the vault's balance is refused")
        void settlingAnOverdrawingWithdrawalIsRefused() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Reserva", ContributionPillar.GOALS);
            transfer(checking.id(), brokerage.id(), 200_000L, null, null, vault.id());

            // A withdrawal that fits today (200k - 150k = 50k >= 0), immediately moved to PENDING —
            // excluded from the vault's settled balance until it is settled (§4.2).
            TransactionResponse withdrawalOutgoing = session.post(
                            path("/transfers"),
                            new TransferRequest(
                                    brokerage.id(), checking.id(), 150_000L, DATE, "Resgate", null, vault.id(), null),
                            TransactionResponse.class)
                    .getBody();
            session.patch(
                    path("/transfers/" + withdrawalOutgoing.id()),
                    new TransferUpdateRequest(
                            null, 150_000L, DATE, "Resgate", null, TransactionStatus.PENDING, null, null),
                    TransactionResponse.class);
            assertThat(balanceOfVault(brokerage.id(), vault.id()))
                    .as("the settled balance is unaffected while the withdrawal is PENDING")
                    .isEqualTo(200_000L);

            // A second, settled withdrawal drops the settled balance to 100k — still enough for the
            // vault to exist, but no longer enough to also cover the 150k still pending.
            transfer(brokerage.id(), checking.id(), 100_000L, null, vault.id(), null);
            assertThat(balanceOfVault(brokerage.id(), vault.id())).isEqualTo(100_000L);

            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transactions/" + withdrawalOutgoing.id() + "/settle"), null, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("VAULT_INSUFFICIENT_BALANCE");
            assertThat(((Number) response.getBody().params().get("shortfallCents")).longValue()).isEqualTo(50_000L);

            // The rejected settle rolled back — the withdrawal is still PENDING, the vault still at 100k.
            assertThat(balanceOfVault(brokerage.id(), vault.id())).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("settling a PENDING withdrawal that still fits succeeds")
        void settlingAWithdrawalThatFitsSucceeds() {
            AccountResponse brokerage = createAccount("Corretora", AccountType.INVESTMENT, 0L);
            VaultResponse vault = createVault(brokerage.id(), "Reserva", ContributionPillar.GOALS);
            transfer(checking.id(), brokerage.id(), 200_000L, null, null, vault.id());

            TransactionResponse withdrawalOutgoing = session.post(
                            path("/transfers"),
                            new TransferRequest(
                                    brokerage.id(), checking.id(), 50_000L, DATE, "Resgate", null, vault.id(), null),
                            TransactionResponse.class)
                    .getBody();
            session.patch(
                    path("/transfers/" + withdrawalOutgoing.id()),
                    new TransferUpdateRequest(
                            null, 50_000L, DATE, "Resgate", null, TransactionStatus.PENDING, null, null),
                    TransactionResponse.class);

            ResponseEntity<TransactionResponse> response = session.post(
                    path("/transactions/" + withdrawalOutgoing.id() + "/settle"), null, TransactionResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(balanceOfVault(brokerage.id(), vault.id())).isEqualTo(150_000L);
        }
    }
}
