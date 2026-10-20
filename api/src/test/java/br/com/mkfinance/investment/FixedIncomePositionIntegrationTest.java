package br.com.mkfinance.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.investment.PositionRequest;
import br.com.mkfinance.api.investment.PositionResponse;
import br.com.mkfinance.api.investment.PositionUpdateRequest;
import br.com.mkfinance.api.investment.UnlinkedContributionResponse;
import br.com.mkfinance.api.transaction.TransferRequest;
import br.com.mkfinance.api.transaction.TransferUpdateRequest;
import br.com.mkfinance.api.vault.VaultRequest;
import br.com.mkfinance.api.vault.VaultResponse;
import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.marketrate.IndexDailyQuote;
import br.com.mkfinance.application.marketrate.IndexHistorySource;
import br.com.mkfinance.application.marketrate.MarketRateQuote;
import br.com.mkfinance.application.marketrate.MarketRateSource;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.MarketRateCode;
import br.com.mkfinance.domain.model.PositionStatus;
import br.com.mkfinance.domain.service.fixedincome.BrazilianBusinessDays;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import br.com.mkfinance.support.ApiSession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Fixed-income positions end to end (DESENVOLVIMENTO-POSICOES.md): both origins, the estimates, the
 * rules and the guards on the purchase transfer, through the real HTTP + security stack against a real
 * Postgres. The Banco Central is replaced by a fake that publishes a flat CDI.
 */
class FixedIncomePositionIntegrationTest extends AbstractPositionIntegrationTest {

    @Nested
    @DisplayName("born from a purchase transfer")
    class FromContribution {

        @Test
        @DisplayName("copies account, amount, date and vault from the purchase and shows the estimates")
        void copiesFromThePurchase() {
            VaultResponse vault = createVault(investment.id(), "Reserva");
            UUID leg = contribute(500_000L, today.minusDays(100), vault.id());

            PositionResponse position = create(linked(leg, InstrumentType.CDB, Indexer.CDI_PERCENT, "110", today.plusDays(265)));

            assertThat(position.origin()).isEqualTo("CONTRIBUTION");
            assertThat(position.contributionTransactionId()).isEqualTo(leg);
            assertThat(position.accountId()).isEqualTo(investment.id());
            assertThat(position.accountName()).isEqualTo("Corretora");
            assertThat(position.vaultId()).isEqualTo(vault.id());
            assertThat(position.vaultName()).isEqualTo("Reserva");
            assertThat(position.principalCents()).isEqualTo(500_000L);
            assertThat(position.purchaseDate()).isEqualTo(today.minusDays(100));
            assertThat(position.status()).isEqualTo(PositionStatus.ACTIVE);
            assertThat(position.awaitingPayment()).isFalse();
            assertThat(position.settlement()).isNull();

            PositionResponse.Estimate estimate = position.estimate();
            assertThat(estimate.today().asOf()).isEqualTo(today);
            assertThat(estimate.today().grossYieldCents()).isPositive();
            assertThat(estimate.today().netCents()).isLessThan(estimate.today().grossCents()); // CDB is taxed
            assertThat(estimate.atMaturity().netCents()).isGreaterThan(estimate.today().netCents());
            assertThat(estimate.today().lastIndexDate()).isNotNull();
        }

        @Test
        @DisplayName("the purchase leaves the list of unlinked contributions once it has a position")
        void unlinkedListShrinks() {
            UUID leg = contribute(500_000L, today.minusDays(10), null);
            assertThat(unlinked()).extracting(UnlinkedContributionResponse::transactionId).contains(leg);

            create(linked(leg, InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(300)));

            assertThat(unlinked()).extracting(UnlinkedContributionResponse::transactionId).doesNotContain(leg);
        }

        @Test
        @DisplayName("only the entering leg of a settled transfer into an investment account is a valid purchase")
        void contributionMustBeAnIncomingTransferLeg() {
            contribute(100_000L, today.minusDays(5), null);

            ErrorResponse unknown = createFails(linked(UUID.randomUUID(), InstrumentType.CDB, Indexer.PREFIXED, "12", today.plusDays(100)));

            assertThat(unknown.code()).isEqualTo(ErrorCode.POSITION_CONTRIBUTION_INVALID);
        }

        @Test
        @DisplayName("a purchase can only originate one position")
        void onlyOnePerPurchase() {
            UUID leg = contribute(500_000L, today.minusDays(10), null);
            create(linked(leg, InstrumentType.CDB, Indexer.PREFIXED, "12", today.plusDays(300)));

            ErrorResponse second = createFails(linked(leg, InstrumentType.CDB, Indexer.PREFIXED, "12", today.plusDays(300)));

            assertThat(second.code()).isEqualTo(ErrorCode.POSITION_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("a title bought before the app")
    class Manual {

        @Test
        @DisplayName("is informed directly, creates no transfer and changes no balance")
        void movesNothing() {
            long investmentBefore = account(investment.id()).balanceCents();
            long checkingBefore = account(checking.id()).balanceCents();

            PositionResponse position = create(manual(investment.id(), 800_000L, today.minusDays(200), null,
                    InstrumentType.CDB, Indexer.CDI_PERCENT, "105", today.plusDays(165)));

            assertThat(position.origin()).isEqualTo("MANUAL");
            assertThat(position.contributionTransactionId()).isNull();
            assertThat(position.principalCents()).isEqualTo(800_000L);
            assertThat(position.vaultId()).isNull();
            assertThat(position.estimate().today().grossYieldCents()).isPositive();
            assertThat(account(investment.id()).balanceCents()).isEqualTo(investmentBefore);
            assertThat(account(checking.id()).balanceCents()).isEqualTo(checkingBefore);
            assertThat(unlinked()).isEmpty();
        }

        @Test
        @DisplayName("must be on an investment or savings account")
        void accountMustHoldInvestments() {
            ErrorResponse error = createFails(manual(checking.id(), 100_000L, today.minusDays(10), null,
                    InstrumentType.CDB, Indexer.PREFIXED, "12", today.plusDays(100)));

            assertThat(error.code()).isEqualTo(ErrorCode.POSITION_ACCOUNT_INVALID);
        }

        @Test
        @DisplayName("the vault, when given, must belong to that account")
        void vaultMustBelongToTheAccount() {
            AccountResponse other = createAccount("Outra corretora", AccountType.INVESTMENT, 0L);
            VaultResponse foreign = createVault(other.id(), "De outra conta");

            ErrorResponse error = createFails(manual(investment.id(), 100_000L, today.minusDays(10), foreign.id(),
                    InstrumentType.CDB, Indexer.PREFIXED, "12", today.plusDays(100)));

            assertThat(error.code()).isEqualTo(ErrorCode.POSITION_VAULT_INVALID);
        }

        @Test
        @DisplayName("takes a vault of its own account")
        void takesItsVault() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");

            PositionResponse position = create(manual(investment.id(), 100_000L, today.minusDays(10), vault.id(),
                    InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(100)));

            assertThat(position.vaultName()).isEqualTo("Sem objetivo");
        }
    }

    @Nested
    @DisplayName("estimates")
    class Estimates {

        @Test
        @DisplayName("a fixed-rate title needs no index and asks the provider for nothing")
        void prefixedAsksForNothing() {
            reset(historySource, marketSource);

            PositionResponse position = create(manual(investment.id(), 1_000_000L, today.minusDays(60), null,
                    InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(300)));

            assertThat(position.estimate().today().lastIndexDate()).isNull();
            assertThat(position.estimate().today().netCents()).isGreaterThan(1_000_000L);
            verifyNoInteractions(historySource, marketSource);
        }

        @Test
        @DisplayName("a title due is valued at its maturity and flagged as waiting for payment")
        void dueTitleWaitsForPayment() {
            PositionResponse position = create(manual(investment.id(), 1_000_000L, today.minusDays(400), null,
                    InstrumentType.LCI, Indexer.PREFIXED, "12", today.minusDays(1)));

            assertThat(position.awaitingPayment()).isTrue();
            assertThat(position.estimate().today().asOf()).isEqualTo(today.minusDays(1));
            assertThat(position.estimate().today().netCents()).isEqualTo(position.estimate().atMaturity().netCents());
        }

        @Test
        @DisplayName("a Selic title is valued with the Selic")
        void selicTitle() {
            PositionResponse position = create(manual(investment.id(), 1_000_000L, today.minusDays(60), null,
                    InstrumentType.TESOURO_SELIC, Indexer.SELIC_PERCENT, "100", today.plusDays(300)));

            assertThat(position.estimate().today().grossYieldCents()).isPositive();
            assertThat(position.estimate().today().custodyCents()).isPositive(); // Tesouro pays custody
        }
    }

    @Nested
    @DisplayName("rules")
    class Rules {

        @Test
        @DisplayName("exactly one origin: both, or neither, is refused")
        void exactlyOneOrigin() {
            UUID leg = contribute(100_000L, today.minusDays(5), null);

            ErrorResponse both = createFails(new PositionRequest(leg, investment.id(), 100_000L, today.minusDays(5), null,
                    null, InstrumentType.CDB, Indexer.PREFIXED, new BigDecimal("12"), today.plusDays(100)));
            ErrorResponse neither = createFails(new PositionRequest(null, null, null, null, null,
                    null, InstrumentType.CDB, Indexer.PREFIXED, new BigDecimal("12"), today.plusDays(100)));
            ErrorResponse incompleteManual = createFails(new PositionRequest(null, investment.id(), null, null, null,
                    null, InstrumentType.CDB, Indexer.PREFIXED, new BigDecimal("12"), today.plusDays(100)));

            assertThat(both.code()).isEqualTo(ErrorCode.POSITION_ORIGIN_INVALID);
            assertThat(neither.code()).isEqualTo(ErrorCode.POSITION_ORIGIN_INVALID);
            assertThat(incompleteManual.code()).isEqualTo(ErrorCode.POSITION_ORIGIN_INVALID);
        }

        @Test
        @DisplayName("IPCA-linked titles and indexers the instrument is not sold with are refused")
        void indexerNotAvailable() {
            ErrorResponse ipca = createFails(manual(investment.id(), 100_000L, today.minusDays(10), null,
                    InstrumentType.TESOURO_IPCA, Indexer.IPCA_PLUS, "6", today.plusDays(100)));
            ErrorResponse mismatch = createFails(manual(investment.id(), 100_000L, today.minusDays(10), null,
                    InstrumentType.CDB, Indexer.SELIC_PERCENT, "100", today.plusDays(100)));

            assertThat(ipca.code()).isEqualTo(ErrorCode.POSITION_INDEXER_NOT_AVAILABLE);
            assertThat(mismatch.code()).isEqualTo(ErrorCode.POSITION_INDEXER_NOT_AVAILABLE);
        }

        @Test
        @DisplayName("the maturity must come after the purchase, and a purchase cannot be in the future")
        void datesMustMakeSense() {
            ErrorResponse beforePurchase = createFails(manual(investment.id(), 100_000L, today.minusDays(10), null,
                    InstrumentType.CDB, Indexer.PREFIXED, "12", today.minusDays(10)));
            ErrorResponse futurePurchase = createFails(manual(investment.id(), 100_000L, today.plusDays(5), null,
                    InstrumentType.CDB, Indexer.PREFIXED, "12", today.plusDays(100)));

            assertThat(beforePurchase.code()).isEqualTo(ErrorCode.POSITION_DATES_INVALID);
            assertThat(futurePurchase.code()).isEqualTo(ErrorCode.POSITION_DATES_INVALID);
        }

        @Test
        @DisplayName("bean validation: negative rate, missing type")
        void beanValidation() {
            ResponseEntity<ErrorResponse> negative = session.post(path("/investments/positions"),
                    manual(investment.id(), 100_000L, today.minusDays(10), null, InstrumentType.CDB, Indexer.PREFIXED, "-1", today.plusDays(100)),
                    ErrorResponse.class);

            assertThat(negative.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(negative.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("editing, deleting and listing")
    class Maintenance {

        @Test
        @DisplayName("an active position takes new terms and a new vault")
        void updatesTermsAndVault() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");
            PositionResponse created = create(manual(investment.id(), 800_000L, today.minusDays(100), null,
                    InstrumentType.CDB, Indexer.CDI_PERCENT, "100", today.plusDays(265)));

            ResponseEntity<PositionResponse> updated = session.put(path("/investments/positions/" + created.id()),
                    new PositionUpdateRequest(900_000L, today.minusDays(90), vault.id(), "CDB 110",
                            InstrumentType.CDB, Indexer.CDI_PERCENT, new BigDecimal("110"), today.plusDays(300)),
                    PositionResponse.class);

            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            PositionResponse body = updated.getBody();
            assertThat(body.rate()).isEqualByComparingTo("110");
            assertThat(body.vaultId()).isEqualTo(vault.id());
            assertThat(body.name()).isEqualTo("CDB 110");
            assertThat(body.principalCents()).isEqualTo(900_000L); // manual: editable
            assertThat(body.purchaseDate()).isEqualTo(today.minusDays(90));
            assertThat(body.maturityDate()).isEqualTo(today.plusDays(300));
        }

        @Test
        @DisplayName("a position born from a purchase keeps the purchase's amount and date, whatever the request says")
        void linkedKeepsItsPurchaseFacts() {
            UUID leg = contribute(500_000L, today.minusDays(100), null);
            PositionResponse created = create(linked(leg, InstrumentType.CDB, Indexer.PREFIXED, "12", today.plusDays(265)));

            PositionResponse updated = session.put(path("/investments/positions/" + created.id()),
                    new PositionUpdateRequest(999_999L, today.minusDays(1), null, "Renomeado",
                            InstrumentType.CDB, Indexer.PREFIXED, new BigDecimal("13"), today.plusDays(265)),
                    PositionResponse.class).getBody();

            assertThat(updated.principalCents()).isEqualTo(500_000L);
            assertThat(updated.purchaseDate()).isEqualTo(today.minusDays(100));
            assertThat(updated.name()).isEqualTo("Renomeado");
            assertThat(updated.rate()).isEqualByComparingTo("13");
        }

        @Test
        @DisplayName("deleting a position frees its purchase to originate another")
        void deleteFreesThePurchase() {
            UUID leg = contribute(500_000L, today.minusDays(10), null);
            PositionResponse created = create(linked(leg, InstrumentType.CDB, Indexer.PREFIXED, "12", today.plusDays(300)));

            ResponseEntity<Void> deleted = session.delete(path("/investments/positions/" + created.id()), Void.class);

            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(unlinked()).extracting(UnlinkedContributionResponse::transactionId).contains(leg);
            assertThat(session.get(path("/investments/positions/" + created.id()), ErrorResponse.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("the list is ordered by maturity, soonest first, and can be filtered by status")
        void listOrderAndFilter() {
            PositionResponse later = create(manual(investment.id(), 100_000L, today.minusDays(10), null,
                    InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(400)));
            PositionResponse sooner = create(manual(investment.id(), 100_000L, today.minusDays(10), null,
                    InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(50)));

            PositionResponse[] all = session.get(path("/investments/positions"), PositionResponse[].class).getBody();
            PositionResponse[] active = session.get(path("/investments/positions?status=ACTIVE"), PositionResponse[].class).getBody();
            PositionResponse[] settled = session.get(path("/investments/positions?status=SETTLED"), PositionResponse[].class).getBody();

            assertThat(all).extracting(PositionResponse::id).containsExactly(sooner.id(), later.id());
            assertThat(active).hasSize(2);
            assertThat(settled).isEmpty();
        }

        @Test
        @DisplayName("another workspace cannot see or touch the position")
        void isolatedPerWorkspace() {
            PositionResponse created = create(manual(investment.id(), 100_000L, today.minusDays(10), null,
                    InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(100)));
            ApiSession intruder = newSession();
            intruder.primeCsrf();
            CurrentUserResponse other = intruder.post("/auth/register",
                    new RegisterRequest("Outro", "pos2+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                    CurrentUserResponse.class).getBody();
            String foreign = "/workspaces/" + other.workspaces().get(0).id() + "/investments/positions/" + created.id();

            assertThat(intruder.get(foreign, ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(intruder.delete(foreign, ErrorResponse.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(session.get(path("/investments/positions/" + created.id()), PositionResponse.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("guards on the purchase transfer")
    class Guards {

        @Test
        @DisplayName("the purchase cannot be deleted while a position depends on it")
        void cannotDeleteThePurchase() {
            UUID leg = contribute(500_000L, today.minusDays(10), null);
            PositionResponse position = create(linked(leg, InstrumentType.CDB, Indexer.PREFIXED, "12", today.plusDays(300)));

            ResponseEntity<ErrorResponse> blocked = session.delete(path("/transactions/" + leg), ErrorResponse.class);

            assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(blocked.getBody().code()).isEqualTo(ErrorCode.TRANSACTION_HAS_POSITION);

            session.delete(path("/investments/positions/" + position.id()), Void.class);
            assertThat(session.delete(path("/transactions/" + leg), Void.class).getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("its amount, date and account cannot change, but its description can")
        void amountAndDateAreLocked() {
            UUID leg = contribute(500_000L, today.minusDays(10), null);
            create(linked(leg, InstrumentType.CDB, Indexer.PREFIXED, "12", today.plusDays(300)));

            ResponseEntity<ErrorResponse> amount = session.patch(path("/transfers/" + leg),
                    new TransferUpdateRequest(null, 600_000L, today.minusDays(10), "Compra CDB", null, null, null, null),
                    ErrorResponse.class);
            ResponseEntity<ErrorResponse> date = session.patch(path("/transfers/" + leg),
                    new TransferUpdateRequest(null, 500_000L, today.minusDays(9), "Compra CDB", null, null, null, null),
                    ErrorResponse.class);
            ResponseEntity<Object> description = session.patch(path("/transfers/" + leg),
                    new TransferUpdateRequest(null, 500_000L, today.minusDays(10), "CDB Inter jan/27", "nota", null, null, null),
                    Object.class);

            assertThat(amount.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(amount.getBody().code()).isEqualTo(ErrorCode.TRANSACTION_HAS_POSITION);
            assertThat(date.getBody().code()).isEqualTo(ErrorCode.TRANSACTION_HAS_POSITION);
            assertThat(description.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
