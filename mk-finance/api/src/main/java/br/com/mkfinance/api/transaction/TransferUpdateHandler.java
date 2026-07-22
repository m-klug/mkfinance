package br.com.mkfinance.api.transaction;

import br.com.mkfinance.application.transaction.TransactionService;
import br.com.mkfinance.application.transaction.TransferService;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransferDirection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs {@code PATCH /transfers/{transactionId}}, wiring the contract's transfer payload onto the
 * mirrored-pair engine that already exists in {@link TransferService}.
 *
 * <p><strong>Why this sits in the api layer.</strong> It adds no rule of its own — every invariant
 * (both legs move together, a leg cannot land on its sibling's account, the pillar is dropped when
 * the destination stops being an INVESTMENT/SAVINGS account) is still decided by
 * {@link TransferService#update}. What it does is span the two calls the endpoint needs in one
 * transaction, which a controller method cannot do. When BE-3 next touches its slice, the natural
 * home for this is a {@code TransferService.update} overload taking the pillar directly; this class
 * exists so the contract could be unblocked without editing another workstream's service.
 *
 * <p><strong>How the pillar override reaches the engine.</strong> {@code TransferService.update}
 * carries the pillar the transfer already has forward, re-resolving it against whatever account the
 * incoming leg now points at. So to re-target a contribution we write the requested pillar onto the
 * incoming leg before delegating: because both legs are loaded inside this same transaction, the
 * instance the engine reads through its own lookup is — by JPA's identity guarantee within a
 * persistence context — the very object written here. The engine then validates it exactly as it
 * validates a pillar on creation, so an override onto a plain checking account is refused with
 * INVALID_CONTRIBUTION_PILLAR rather than silently inflating a pillar.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class TransferUpdateHandler {

    private final TransactionService transactionService;
    private final TransferService transferService;
    private final TransactionMapper mapper;

    /**
     * @param transactionId either leg of the transfer; the response describes this one
     * @throws AppException TRANSACTION_NOT_FOUND when the id is not a transfer leg — a plain
     *     INCOME/EXPENSE id included, since "no such transfer" is the honest answer and leaks
     *     nothing about what the id really is
     */
    public TransactionResponse update(UUID workspaceId, UUID transactionId, TransferUpdateRequest request) {
        Transaction leg = transactionService.get(workspaceId, transactionId);
        if (!leg.isTransfer()) {
            throw AppException.transactionNotFound();
        }

        Transaction incoming = incomingLegOf(workspaceId, leg);
        if (request.contributionPillar() != null) {
            incoming.setContributionPillar(request.contributionPillar());
        }

        Transaction updated = transferService.update(workspaceId, leg, mapper.toInput(request));
        requirePillarWasApplied(request, incoming);
        // The pillar lives only on the incoming leg so it can never be double-counted; echo it
        // alongside whichever leg was addressed, the way POST /transfers already does.
        return mapper.toResponse(updated, incoming.getContributionPillar());
    }

    /**
     * Refuses a pillar the engine did not keep, so an explicit override onto a destination that
     * takes no contribution fails loudly instead of vanishing — the same answer
     * {@code POST /transfers} gives (INVALID_CONTRIBUTION_PILLAR), so create and edit agree.
     *
     * <p>The check reads the engine's own verdict rather than re-deriving which account types accept
     * a contribution: that rule stays in one place. Throwing here rolls the whole edit back, since
     * this method runs in a transaction.
     *
     * <p>Only an <em>explicit</em> override is policed. Omitting the pillar still means "leave it
     * alone", and moving a contribution to a plain account without naming a pillar still drops it
     * silently, which is deliberate and covered by BE-3's own tests.
     */
    private void requirePillarWasApplied(TransferUpdateRequest request, Transaction incoming) {
        if (request.contributionPillar() != null
                && request.contributionPillar() != incoming.getContributionPillar()) {
            throw AppException.invalidContributionPillar();
        }
    }

    private Transaction incomingLegOf(UUID workspaceId, Transaction leg) {
        return leg.getTransferDirection() == TransferDirection.IN
                ? leg
                : transactionService.get(workspaceId, leg.getTransferPairId());
    }
}
