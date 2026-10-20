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
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.service.ReimbursementRules;
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
                request.tags(),
                null,
                request.reimbursableCents());
    }

    public TransferUpdateInput toInput(TransferUpdateRequest request) {
        return new TransferUpdateInput(
                request.accountId(),
                request.amountCents(),
                request.date(),
                request.description(),
                request.note(),
                request.status(),
                request.contributionPillar(),
                request.vaultId());
    }

    public TransferInput toInput(TransferRequest request) {
        return new TransferInput(
                request.fromAccountId(),
                request.toAccountId(),
                request.amountCents(),
                request.date(),
                request.description(),
                request.contributionPillar(),
                request.fromVaultId(),
                request.toVaultId());
    }

    /** {@code tags}/{@code vaultId} default to empty/null — callers with real values use the overloads below. */
    public TransactionResponse toResponse(Transaction transaction) {
        return toResponse(transaction, transaction.getContributionPillar(), List.of(), null);
    }

    public TransactionResponse toResponse(Transaction transaction, List<Tag> tags) {
        return toResponse(transaction, transaction.getContributionPillar(), tagMapper.toResponseList(tags), null);
    }

    /** Overload for a single-transaction response that also carries the extrato's vault badge (BE-10). */
    public TransactionResponse toResponse(Transaction transaction, List<Tag> tags, UUID vaultId) {
        return toResponse(transaction, transaction.getContributionPillar(), tagMapper.toResponseList(tags), vaultId);
    }

    /**
     * The contract has {@code POST /transfers} return the <em>outgoing</em> leg, but the
     * contribution pillar is stored on the incoming one (so it can never be double-counted). This
     * overload lets the transfer endpoint answer with the pillar that was actually applied instead
     * of a misleading null. Transfer legs never carry tags (BE-8 scope), so {@code tags} is empty.
     */
    public TransactionResponse toResponse(Transaction transaction, ContributionPillar contributionPillar, UUID vaultId) {
        return toResponse(transaction, contributionPillar, List.of(), vaultId);
    }

    /** Overload carrying how much is linked by reimbursements (ADR-0002) — INCOME or EXPENSE rows. */
    public TransactionResponse toResponse(Transaction transaction, List<Tag> tags, UUID vaultId, long linkedCents) {
        return toResponse(
                transaction, transaction.getContributionPillar(), tagMapper.toResponseList(tags), vaultId, linkedCents);
    }

    private TransactionResponse toResponse(
            Transaction transaction, ContributionPillar contributionPillar, List<TagResponse> tags, UUID vaultId) {
        return toResponse(transaction, contributionPillar, tags, vaultId, 0);
    }

    private TransactionResponse toResponse(
            Transaction transaction,
            ContributionPillar contributionPillar,
            List<TagResponse> tags,
            UUID vaultId,
            long linkedCents) {
        boolean expense = transaction.getType() == TransactionType.EXPENSE;
        Long reimbursable = transaction.getReimbursableCents();
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
                vaultId,
                transaction.getRecurrenceId(),
                tags,
                reimbursable,
                linkedCents,
                expense && reimbursable != null ? ReimbursementRules.pending(reimbursable, linkedCents) : null,
                expense ? ReimbursementRules.effectiveExpense(transaction.getAmountCents(), linkedCents) : null);
    }

    /** {@code vaultId} is the outgoing leg's own — the leg the contract has {@code POST /transfers} return. */
    public TransactionResponse toResponse(TransferPair pair, UUID vaultId) {
        return toResponse(pair.outgoing(), pair.incoming().getContributionPillar(), vaultId);
    }

    public TransactionPageResponse toPageResponse(
            Page<Transaction> page,
            Map<UUID, List<Tag>> tagsByTransactionId,
            Map<UUID, UUID> vaultIdByTransactionId,
            Map<UUID, Long> linkedByTransactionId) {
        return new TransactionPageResponse(
                page.getContent().stream()
                        .map(transaction -> toResponse(
                                transaction,
                                tagsByTransactionId.getOrDefault(transaction.getId(), List.of()),
                                vaultIdByTransactionId.get(transaction.getId()),
                                linkedByTransactionId.getOrDefault(transaction.getId(), 0L)))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}
