package br.com.mkfinance.api.transaction;

import br.com.mkfinance.application.transaction.TransactionInput;
import br.com.mkfinance.application.transaction.TransferInput;
import br.com.mkfinance.application.transaction.TransferPair;
import br.com.mkfinance.application.transaction.TransferUpdateInput;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionInput toInput(TransactionRequest request) {
        return new TransactionInput(
                request.accountId(),
                request.categoryId(),
                request.type(),
                request.amountCents(),
                request.date(),
                request.description(),
                request.note(),
                request.status());
    }

    public TransferUpdateInput toInput(TransferUpdateRequest request) {
        return new TransferUpdateInput(
                request.accountId(),
                request.amountCents(),
                request.date(),
                request.description(),
                request.note(),
                request.status(),
                request.contributionPillar());
    }

    public TransferInput toInput(TransferRequest request) {
        return new TransferInput(
                request.fromAccountId(),
                request.toAccountId(),
                request.amountCents(),
                request.date(),
                request.description(),
                request.contributionPillar());
    }

    public TransactionResponse toResponse(Transaction transaction) {
        return toResponse(transaction, transaction.getContributionPillar());
    }

    /**
     * The contract has {@code POST /transfers} return the <em>outgoing</em> leg, but the
     * contribution pillar is stored on the incoming one (so it can never be double-counted). This
     * overload lets the transfer endpoint answer with the pillar that was actually applied instead
     * of a misleading null.
     */
    public TransactionResponse toResponse(Transaction transaction, ContributionPillar contributionPillar) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getCategoryId(),
                transaction.getType(),
                transaction.getAmountCents(),
                transaction.getDate(),
                transaction.getDescription(),
                transaction.getNote(),
                transaction.getStatus(),
                transaction.getTransferPairId(),
                transaction.getTransferDirection(),
                contributionPillar);
    }

    public TransactionResponse toResponse(TransferPair pair) {
        return toResponse(pair.outgoing(), pair.incoming().getContributionPillar());
    }

    public TransactionPageResponse toPageResponse(Page<Transaction> page) {
        return new TransactionPageResponse(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}
