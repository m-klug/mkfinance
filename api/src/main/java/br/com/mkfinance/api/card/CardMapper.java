package br.com.mkfinance.api.card;

import br.com.mkfinance.api.card.CardPurchaseResponse.InstallmentResponse;
import br.com.mkfinance.api.card.StatementDetailResponse.StatementItemResponse;
import br.com.mkfinance.application.card.CardInput;
import br.com.mkfinance.application.card.CardPurchaseInput;
import br.com.mkfinance.application.card.StatementPaymentInput;
import br.com.mkfinance.application.card.StatementView;
import br.com.mkfinance.domain.model.Card;
import br.com.mkfinance.domain.model.CardPurchase;
import br.com.mkfinance.domain.model.Installment;
import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.infrastructure.persistence.StatementItemRow;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Translation between the contract's DTOs and the domain. No business decisions live here. */
@Component
public class CardMapper {

    /** The contract defaults an omitted {@code installmentCount} to 1 (an outright purchase). */
    private static final short SINGLE_INSTALLMENT = 1;

    public CardInput toInput(CardRequest request) {
        return new CardInput(
                request.name(),
                request.brand(),
                request.limitCents(),
                request.closingDay(),
                request.dueDay(),
                request.defaultPaymentAccountId(),
                request.color());
    }

    public CardPurchaseInput toInput(CardPurchaseRequest request) {
        short installments = request.installmentCount() != null ? request.installmentCount() : SINGLE_INSTALLMENT;
        return new CardPurchaseInput(
                request.categoryId(),
                request.description(),
                request.totalAmountCents(),
                request.purchaseDate(),
                installments);
    }

    public StatementPaymentInput toInput(StatementPaymentRequest request) {
        return new StatementPaymentInput(request.accountId(), request.amountCents(), request.paymentDate());
    }

    /** {@code availableLimitCents} is nullable in the contract; null means "not computed here". */
    public CardResponse toResponse(Card card, Long availableLimitCents) {
        return new CardResponse(
                card.getId(),
                card.getName(),
                card.getBrand(),
                card.getLimitCents(),
                availableLimitCents,
                card.getClosingDay(),
                card.getDueDay(),
                card.getDefaultPaymentAccountId(),
                card.getColor(),
                card.isArchived());
    }

    public CardPurchaseResponse toResponse(
            CardPurchase purchase, List<Installment> installments, Map<UUID, YearMonth> statementMonths) {
        List<InstallmentResponse> lines = installments.stream()
                .map(installment -> toResponse(installment, statementMonths.get(installment.getStatementId())))
                .toList();
        return new CardPurchaseResponse(
                purchase.getId(),
                purchase.getCardId(),
                purchase.getCategoryId(),
                purchase.getDescription(),
                purchase.getTotalAmountCents(),
                purchase.getPurchaseDate(),
                purchase.getInstallmentCount(),
                lines);
    }

    public StatementResponse toResponse(StatementView view) {
        Statement statement = view.statement();
        return new StatementResponse(
                statement.getId(),
                statement.getCardId(),
                statement.referenceYearMonth().toString(),
                statement.getClosingDate(),
                statement.getDueDate(),
                view.status(),
                view.totalCents(),
                view.paidCents());
    }

    public StatementDetailResponse toResponse(StatementView.Detail detail) {
        StatementView view = detail.summary();
        Statement statement = view.statement();
        return new StatementDetailResponse(
                statement.getId(),
                statement.getCardId(),
                statement.referenceYearMonth().toString(),
                statement.getClosingDate(),
                statement.getDueDate(),
                view.status(),
                view.totalCents(),
                view.paidCents(),
                detail.items().stream().map(this::toResponse).toList());
    }

    private InstallmentResponse toResponse(Installment installment, YearMonth statementMonth) {
        return new InstallmentResponse(
                installment.getId(),
                installment.getNumber(),
                installment.getAmountCents(),
                installment.getStatementId(),
                statementMonth != null ? statementMonth.toString() : null);
    }

    private StatementItemResponse toResponse(StatementItemRow row) {
        return new StatementItemResponse(
                row.installmentId(),
                row.purchaseId(),
                row.description(),
                row.categoryId(),
                row.amountCents(),
                row.purchaseDate(),
                row.installmentNumber(),
                row.installmentCount());
    }
}
