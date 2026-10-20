package br.com.mkfinance.investment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.investment.PositionResponse;
import br.com.mkfinance.api.investment.PositionReturnTransferRequest;
import br.com.mkfinance.api.investment.PositionSettleRequest;
import br.com.mkfinance.api.investment.PositionUpdateRequest;
import br.com.mkfinance.api.transaction.TransactionRequest;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.api.transaction.TransferRequest;
import br.com.mkfinance.api.vault.VaultCompositionResponse;
import br.com.mkfinance.api.vault.VaultResponse;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.PositionStatus;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The payment of a title (DESENVOLVIMENTO-POSICOES.md P4): the real yield lands in the title's vault
 * (or stays unallocated), nothing else is booked, and the whole thing can be undone and redone.
 */
class FixedIncomePositionSettlementIntegrationTest extends AbstractPositionIntegrationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    private ResponseEntity<PositionResponse> settle(UUID positionId, LocalDate date, long received, UUID returnTransfer) {
        return session.post(path("/investments/positions/" + positionId + "/settle"),
                new PositionSettleRequest(date, received, returnTransfer), PositionResponse.class);
    }

    private ErrorResponse settleFails(UUID positionId, LocalDate date, long received) {
        ResponseEntity<ErrorResponse> response = session.post(path("/investments/positions/" + positionId + "/settle"),
                new PositionSettleRequest(date, received, null), ErrorResponse.class);
        assertThat(response.getStatusCode().isError()).isTrue();
        return response.getBody();
    }

    private VaultCompositionResponse composition(UUID accountId) {
        return session.get(path("/accounts/" + accountId + "/vaults"), VaultCompositionResponse.class).getBody();
    }

    /** The INCOME rows of an account (the booked yields), newest first. */
    private List<Transaction> incomesOf(UUID accountId) {
        return transactionRepository.findAll().stream()
                .filter(t -> t.getAccountId().equals(accountId) && t.getType() == TransactionType.INCOME)
                .toList();
    }

    private PositionResponse purchasedTitle(VaultResponse vault, long principal) {
        UUID leg = contribute(principal, today.minusDays(120), vault == null ? null : vault.id());
        return create(linked(leg, InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(200)));
    }

    @Nested
    @DisplayName("paying a title")
    class Paying {

        @Test
        @DisplayName("books only the real yield, in the title's vault, and remembers estimated next to received")
        void booksTheYieldInTheVault() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");
            PositionResponse position = purchasedTitle(vault, 500_000L);
            long checkingBefore = account(checking.id()).balanceCents();

            ResponseEntity<PositionResponse> response = settle(position.id(), today, 520_000L, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PositionResponse paid = response.getBody();
            assertThat(paid.status()).isEqualTo(PositionStatus.SETTLED);
            assertThat(paid.awaitingPayment()).isFalse();
            assertThat(paid.estimate()).isNull();
            assertThat(paid.settlement().settledDate()).isEqualTo(today);
            assertThat(paid.settlement().receivedCents()).isEqualTo(520_000L);
            assertThat(paid.settlement().yieldCents()).isEqualTo(20_000L);
            assertThat(paid.settlement().estimatedNetCents()).isPositive();
            assertThat(paid.settlement().returnTransferTransactionId()).isNull();

            VaultCompositionResponse composition = composition(investment.id());
            assertThat(composition.balanceCents()).isEqualTo(520_000L);
            assertThat(composition.vaults().get(0).balanceCents()).isEqualTo(520_000L);
            assertThat(composition.unallocatedCents()).isZero();
            assertThat(account(checking.id()).balanceCents()).isEqualTo(checkingBefore); // no transfer was booked
            assertThat(incomesOf(investment.id())).hasSize(1);
            assertThat(incomesOf(investment.id()).get(0).getAmountCents()).isEqualTo(20_000L);
        }

        @Test
        @DisplayName("receiving exactly the principal books no yield")
        void zeroYield() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");
            PositionResponse position = purchasedTitle(vault, 500_000L);

            PositionResponse paid = settle(position.id(), today, 500_000L, null).getBody();

            assertThat(paid.status()).isEqualTo(PositionStatus.SETTLED);
            assertThat(paid.settlement().yieldCents()).isZero();
            assertThat(incomesOf(investment.id())).isEmpty();
            assertThat(composition(investment.id()).balanceCents()).isEqualTo(500_000L);
        }

        @Test
        @DisplayName("a title without a vault leaves its yield unallocated, even when the account has other vaults")
        void unallocatedYield() {
            VaultResponse other = createVault(investment.id(), "Viagem");
            contribute(300_000L, today.minusDays(50), other.id());
            PositionResponse manualTitle = create(manual(investment.id(), 400_000L, today.minusDays(150), null,
                    InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(100)));

            settle(manualTitle.id(), today, 410_000L, null);

            VaultCompositionResponse composition = composition(investment.id());
            assertThat(composition.vaults().get(0).balanceCents()).isEqualTo(300_000L); // not diluted into "Viagem"
            assertThat(composition.unallocatedCents()).isEqualTo(10_000L);
            assertThat(composition.balanceCents()).isEqualTo(310_000L);
        }

        @Test
        @DisplayName("an account with no vault at all simply gets the yield")
        void noVaultsAtAll() {
            PositionResponse manualTitle = create(manual(investment.id(), 400_000L, today.minusDays(150), null,
                    InstrumentType.LCI, Indexer.PREFIXED, "12", today.plusDays(100)));
            long before = account(investment.id()).balanceCents();

            settle(manualTitle.id(), today, 415_000L, null);

            assertThat(account(investment.id()).balanceCents()).isEqualTo(before + 15_000L);
        }

        @Test
        @DisplayName("a post-fixed title also remembers what the app estimated")
        void estimatedNetForPostFixed() {
            PositionResponse title = create(manual(investment.id(), 1_000_000L, today.minusDays(100), null,
                    InstrumentType.CDB, Indexer.CDI_PERCENT, "110", today.plusDays(265)));

            PositionResponse paid = settle(title.id(), today, 1_030_000L, null).getBody();

            // ~70 business days at 110% of a 13,65% CDI: about +4%, less 22,5% income tax on the yield.
            assertThat(paid.settlement().estimatedNetCents()).isBetween(1_025_000L, 1_036_000L);
        }
    }

    @Nested
    @DisplayName("what a payment must satisfy")
    class Rules {

        @Test
        @DisplayName("receiving less than the principal is refused, naming the principal")
        void belowPrincipal() {
            PositionResponse position = purchasedTitle(null, 500_000L);

            ResponseEntity<ErrorResponse> response = session.post(path("/investments/positions/" + position.id() + "/settle"),
                    new PositionSettleRequest(today, 499_999L, null), ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.POSITION_RECEIVED_BELOW_PRINCIPAL);
            assertThat(response.getBody().params()).containsEntry("principalCents", 500_000);
        }

        @Test
        @DisplayName("the date cannot be before the purchase nor in the future")
        void dates() {
            PositionResponse position = purchasedTitle(null, 500_000L);

            assertThat(settleFails(position.id(), today.minusDays(121), 510_000L).code()).isEqualTo(ErrorCode.POSITION_DATES_INVALID);
            assertThat(settleFails(position.id(), today.plusDays(1), 510_000L).code()).isEqualTo(ErrorCode.POSITION_DATES_INVALID);
        }

        @Test
        @DisplayName("a title is paid once")
        void paidOnce() {
            PositionResponse position = purchasedTitle(null, 500_000L);
            settle(position.id(), today, 510_000L, null);

            ResponseEntity<ErrorResponse> again = session.post(path("/investments/positions/" + position.id() + "/settle"),
                    new PositionSettleRequest(today, 510_000L, null), ErrorResponse.class);

            assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(again.getBody().code()).isEqualTo(ErrorCode.POSITION_SETTLED);
        }

        @Test
        @DisplayName("an amount of zero is refused by validation")
        void validation() {
            PositionResponse position = purchasedTitle(null, 500_000L);

            ResponseEntity<ErrorResponse> response = session.post(path("/investments/positions/" + position.id() + "/settle"),
                    new PositionSettleRequest(today, 0L, null), ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("a paid position is neither edited nor deleted, and shows under the settled filter")
        void settledIsFrozen() {
            PositionResponse position = purchasedTitle(null, 500_000L);
            settle(position.id(), today, 510_000L, null);

            ResponseEntity<ErrorResponse> edit = session.put(path("/investments/positions/" + position.id()),
                    new PositionUpdateRequest(null, null, null, "x", InstrumentType.LCI, Indexer.PREFIXED,
                            new BigDecimal("12"), today.plusDays(200)), ErrorResponse.class);
            ResponseEntity<ErrorResponse> delete = session.delete(path("/investments/positions/" + position.id()), ErrorResponse.class);
            PositionResponse[] settled = session.get(path("/investments/positions?status=SETTLED"), PositionResponse[].class).getBody();

            assertThat(edit.getBody().code()).isEqualTo(ErrorCode.POSITION_SETTLED);
            assertThat(delete.getBody().code()).isEqualTo(ErrorCode.POSITION_SETTLED);
            assertThat(settled).extracting(PositionResponse::id).containsExactly(position.id());
        }
    }

    @Nested
    @DisplayName("the booked yield is protected")
    class YieldGuard {

        @Test
        @DisplayName("it cannot be deleted from the extrato, and its amount cannot change, while the position holds it")
        void locked() {
            PositionResponse position = purchasedTitle(null, 500_000L);
            settle(position.id(), today, 520_000L, null);
            Transaction yield = incomesOf(investment.id()).get(0);

            ResponseEntity<ErrorResponse> delete = session.delete(path("/transactions/" + yield.getId()), ErrorResponse.class);
            ResponseEntity<ErrorResponse> edit = session.patch(path("/transactions/" + yield.getId()),
                    new TransactionRequest(investment.id(), yield.getCategoryId(), TransactionType.INCOME, 99_999L,
                            yield.getDate(), yield.getDescription(), null, null, null, null),
                    ErrorResponse.class);
            ResponseEntity<TransactionResponse> rename = session.patch(path("/transactions/" + yield.getId()),
                    new TransactionRequest(investment.id(), yield.getCategoryId(), TransactionType.INCOME, 20_000L,
                            yield.getDate(), "Renomeado", "nota", null, null, null),
                    TransactionResponse.class);

            assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(delete.getBody().code()).isEqualTo(ErrorCode.TRANSACTION_HAS_POSITION);
            assertThat(edit.getBody().code()).isEqualTo(ErrorCode.TRANSACTION_HAS_POSITION);
            assertThat(rename.getStatusCode()).isEqualTo(HttpStatus.OK); // description and note stay free
        }
    }

    @Nested
    @DisplayName("undoing a payment")
    class Undoing {

        @Test
        @DisplayName("removes the booked yield and returns the position to active, ready to be paid again")
        void undoAndPayAgain() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");
            PositionResponse position = purchasedTitle(vault, 500_000L);
            settle(position.id(), today, 520_000L, null);

            ResponseEntity<PositionResponse> undone = session.post(path("/investments/positions/" + position.id() + "/unsettle"),
                    null, PositionResponse.class);

            assertThat(undone.getStatusCode()).isEqualTo(HttpStatus.OK);
            PositionResponse active = undone.getBody();
            assertThat(active.status()).isEqualTo(PositionStatus.ACTIVE);
            assertThat(active.settlement()).isNull();
            assertThat(active.estimate()).isNotNull();
            assertThat(incomesOf(investment.id())).isEmpty();
            assertThat(composition(investment.id()).balanceCents()).isEqualTo(500_000L);
            assertThat(composition(investment.id()).vaults().get(0).balanceCents()).isEqualTo(500_000L);

            PositionResponse repaid = settle(position.id(), today, 530_000L, null).getBody();
            assertThat(repaid.settlement().yieldCents()).isEqualTo(30_000L);
            assertThat(composition(investment.id()).vaults().get(0).balanceCents()).isEqualTo(530_000L);
        }

        @Test
        @DisplayName("undoing a zero-yield payment has nothing to remove and still works")
        void undoZeroYield() {
            PositionResponse position = purchasedTitle(null, 500_000L);
            settle(position.id(), today, 500_000L, null);

            PositionResponse undone = session.post(path("/investments/positions/" + position.id() + "/unsettle"),
                    null, PositionResponse.class).getBody();

            assertThat(undone.status()).isEqualTo(PositionStatus.ACTIVE);
        }

        @Test
        @DisplayName("only a paid position can be undone")
        void onlyPaid() {
            PositionResponse position = purchasedTitle(null, 500_000L);

            ResponseEntity<ErrorResponse> response = session.post(path("/investments/positions/" + position.id() + "/unsettle"),
                    null, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.POSITION_NOT_SETTLED);
        }
    }

    @Nested
    @DisplayName("linking the transfer back to checking")
    class ReturnTransfer {

        /** Moves money out of the investment account back to checking, like the credit of a paid title. */
        private UUID transferBack(long cents, UUID fromVaultId) {
            ResponseEntity<TransactionResponse> response = session.post(path("/transfers"),
                    new TransferRequest(investment.id(), checking.id(), cents, today, "Resgate CDB", null, fromVaultId, null),
                    TransactionResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            return response.getBody().id();
        }

        private ResponseEntity<PositionResponse> link(UUID positionId, UUID transactionId) {
            return session.put(path("/investments/positions/" + positionId + "/return-transfer"),
                    new PositionReturnTransferRequest(transactionId), PositionResponse.class);
        }

        @Test
        @DisplayName("the whole flow: pay, then the credit comes back out of the vault, then it is linked")
        void payThenReturnThenLink() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");
            PositionResponse position = purchasedTitle(vault, 500_000L);

            settle(position.id(), today, 520_000L, null);
            UUID back = transferBack(520_000L, vault.id()); // possible only because the yield was booked first
            PositionResponse linked = link(position.id(), back).getBody();

            assertThat(linked.settlement().returnTransferTransactionId()).isEqualTo(back);
            assertThat(composition(investment.id()).balanceCents()).isZero();
            assertThat(session.delete(path("/transactions/" + back), ErrorResponse.class).getBody().code())
                    .isEqualTo(ErrorCode.TRANSACTION_HAS_POSITION);

            PositionResponse unlinked = link(position.id(), null).getBody();
            assertThat(unlinked.settlement().returnTransferTransactionId()).isNull();
            assertThat(session.delete(path("/transactions/" + back), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("the transfer can also be linked in the payment itself")
        void linkWhilePaying() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");
            PositionResponse position = purchasedTitle(vault, 500_000L);
            UUID back = transferBack(500_000L, vault.id());

            PositionResponse paid = settle(position.id(), today, 520_000L, back).getBody();

            assertThat(paid.settlement().returnTransferTransactionId()).isEqualTo(back);
        }

        @Test
        @DisplayName("undoing the payment forgets the link, so the transfer is free again")
        void undoForgetsTheLink() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");
            PositionResponse position = purchasedTitle(vault, 500_000L);
            UUID back = transferBack(500_000L, vault.id());
            settle(position.id(), today, 520_000L, back);

            session.post(path("/investments/positions/" + position.id() + "/unsettle"), null, PositionResponse.class);

            assertThat(session.delete(path("/transactions/" + back), Void.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("only the outgoing leg from the position's own account is accepted")
        void wrongLegs() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");
            PositionResponse position = purchasedTitle(vault, 500_000L);
            UUID purchaseOutgoingFromChecking = lastOutgoingLegId;
            UUID purchaseIncoming = position.contributionTransactionId();
            settle(position.id(), today, 520_000L, null);

            ResponseEntity<ErrorResponse> incoming = session.put(path("/investments/positions/" + position.id() + "/return-transfer"),
                    new PositionReturnTransferRequest(purchaseIncoming), ErrorResponse.class);
            ResponseEntity<ErrorResponse> otherAccount = session.put(path("/investments/positions/" + position.id() + "/return-transfer"),
                    new PositionReturnTransferRequest(purchaseOutgoingFromChecking), ErrorResponse.class);
            ResponseEntity<ErrorResponse> unknown = session.put(path("/investments/positions/" + position.id() + "/return-transfer"),
                    new PositionReturnTransferRequest(UUID.randomUUID()), ErrorResponse.class);

            for (ResponseEntity<ErrorResponse> response : List.of(incoming, otherAccount, unknown)) {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody().code()).isEqualTo(ErrorCode.POSITION_RETURN_TRANSFER_INVALID);
            }
        }

        @Test
        @DisplayName("a transfer already claimed by another position is refused")
        void claimedByAnother() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");
            PositionResponse first = purchasedTitle(vault, 500_000L);
            PositionResponse second = purchasedTitle(vault, 300_000L);
            settle(first.id(), today, 520_000L, null);
            settle(second.id(), today, 310_000L, null);
            UUID back = transferBack(520_000L, vault.id());
            link(first.id(), back);

            ResponseEntity<ErrorResponse> response = session.put(path("/investments/positions/" + second.id() + "/return-transfer"),
                    new PositionReturnTransferRequest(back), ErrorResponse.class);

            assertThat(response.getBody().code()).isEqualTo(ErrorCode.POSITION_RETURN_TRANSFER_INVALID);
        }

        @Test
        @DisplayName("only a paid position takes a link")
        void onlyPaidTakesALink() {
            VaultResponse vault = createVault(investment.id(), "Sem objetivo");
            PositionResponse position = purchasedTitle(vault, 500_000L);
            UUID back = transferBack(500_000L, vault.id());

            ResponseEntity<ErrorResponse> response = session.put(path("/investments/positions/" + position.id() + "/return-transfer"),
                    new PositionReturnTransferRequest(back), ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.POSITION_NOT_SETTLED);
        }
    }
}
