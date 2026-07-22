package br.com.mkfinance.api.card;

import br.com.mkfinance.domain.model.StatementStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contract: {@code StatementDetail} — {@code Statement} plus its line items.
 *
 * <p>Flattened rather than nested because the contract composes it with {@code allOf}, which orval
 * generates as a single flat object on the client.
 */
public record StatementDetailResponse(
        UUID id,
        UUID cardId,
        String referenceMonth,
        LocalDate closingDate,
        LocalDate dueDate,
        StatementStatus status,
        long totalCents,
        long paidCents,
        List<StatementItemResponse> items,
        List<StatementPaymentResponse> payments) {

    /**
     * One payment made against the statement. {@code id} is what the reversal endpoint takes, and
     * {@code transactionId} lets the client link the bill to the debit row in the extrato.
     */
    public record StatementPaymentResponse(
            UUID id, UUID accountId, long amountCents, LocalDate paymentDate, UUID transactionId) {
    }

    /** One installment as billed, with the purchase context needed to recognize it. */
    public record StatementItemResponse(
            UUID installmentId,
            UUID purchaseId,
            String description,
            UUID categoryId,
            long amountCents,
            LocalDate purchaseDate,
            short installmentNumber,
            short installmentCount) {
    }
}
