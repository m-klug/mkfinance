package br.com.mkfinance.api.transaction;

import br.com.mkfinance.api.tag.TagMapper;
import br.com.mkfinance.api.tag.TagResponse;
import br.com.mkfinance.application.transaction.TransactionInput;
import br.com.mkfinance.application.transaction.TransferInput;
import br.com.mkfinance.application.transaction.TransferPair;
import br.com.mkfinance.application.transaction.TransferUpdateInput;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.Tag;
import br.com.mkfinance.domain.model.Transaction;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionMapper {

    private final TagMapper tagMapper;

    public TransactionInput toInput(TransactionRequest request) {
        return new TransactionInput(
                request.accountId(),
                request.categoryId(),
                request.type(),
                request.amountCents(),
                request.date(),
                request.description(),
                request.note(),
                request.status(),
                request.tags());
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

    /** {@code tags} defaults to empty — callers that resolved real tags use the overload below. */
    public TransactionResponse toResponse(Transaction transaction) {
        return toResponse(transaction, transaction.getContributionPillar(), List.of());
    }

    public TransactionResponse toResponse(Transaction transaction, List<Tag> tags) {
        return toResponse(transaction, transaction.getContributionPillar(), tagMapper.toResponseList(tags));
    }

    /**
     * The contract has {@code POST /transfers} return the <em>outgoing</em> leg, but the
     * contribution pillar is stored on the incoming one (so it can never be double-counted). This
     * overload lets the transfer endpoint answer with the pillar that was actually applied instead
     * of a misleading null. Transfer legs never carry tags (BE-8 scope), so {@code tags} is empty.
     */
    public TransactionResponse toResponse(Transaction transaction, ContributionPillar contributionPillar) {
        return toResponse(transaction, contributionPillar, List.of());
    }

    private TransactionResponse toResponse(
            Transaction transaction, ContributionPillar contributionPillar, List<TagResponse> tags) {
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
                contributionPillar,
                transaction.getRecurrenceId(),
                tags);
    }

    public TransactionResponse toResponse(TransferPair pair) {
        return toResponse(pair.outgoing(), pair.incoming().getContributionPillar());
    }

    public TransactionPageResponse toPageResponse(Page<Transaction> page, Map<UUID, List<Tag>> tagsByTransactionId) {
        return new TransactionPageResponse(
                page.getContent().stream()
                        .map(transaction -> toResponse(
                                transaction, tagsByTransactionId.getOrDefault(transaction.getId(), List.of())))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}
