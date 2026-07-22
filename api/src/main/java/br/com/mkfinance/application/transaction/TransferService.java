package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The mirrored-pair engine (PLANEJAMENTO §5 R4 and R9).
 *
 * <p>A transfer is never a single row: it is an {@link TransferDirection#OUT} leg on the source
 * account and an {@link TransferDirection#IN} leg on the destination, each pointing at the other
 * through {@code transfer_pair_id}. Every mutation here — create, edit, settle, delete — touches
 * <em>both</em> legs inside one transaction, so the two sides can never drift apart. Because the
 * legs carry opposite signs on different accounts, a transfer moves money without ever producing
 * income or expense (R4).
 *
 * <p>Contribution ("aporte", R9): when the destination is an INVESTMENT or SAVINGS account, the
 * transfer consumes a pillar's allocation without being an expense. The pillar defaults to the
 * destination account's {@code defaultContributionPillar} and can be overridden per transfer — the
 * same brokerage account may receive a GOALS contribution one month and a FINANCIAL_FREEDOM one
 * the next. It is stored on the IN leg only (DB check constraint), so summing it per pillar can
 * never count a single transfer twice.
 *
 * <p>This service deliberately does not depend on {@code TransactionService}: the dependency runs
 * the other way, so there is no cycle.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    /**
     * Creates both legs atomically and returns them. Transfers start SETTLED — the contract's
     * {@code TransferRequest} has no status field; a transfer can still be moved to PENDING later
     * through the transaction PATCH endpoint.
     */
    public TransferPair create(UUID workspaceId, TransferInput input) {
        if (input.fromAccountId() == null
                || input.toAccountId() == null
                || input.fromAccountId().equals(input.toAccountId())) {
            throw AppException.insufficientTransferAccounts();
        }
        Account source = TransactionLookup.ownedAccount(accountRepository, workspaceId, input.fromAccountId());
        Account destination = TransactionLookup.ownedAccount(accountRepository, workspaceId, input.toAccountId());
        ContributionPillar pillar = resolveContributionPillar(destination, input.contributionPillar());

        Transaction outgoing = leg(
                workspaceId,
                source.getId(),
                TransferDirection.OUT,
                input.amountCents(),
                input.date(),
                input.description(),
                TransactionStatus.SETTLED,
                null);
        Transaction incoming = leg(
                workspaceId,
                destination.getId(),
                TransferDirection.IN,
                input.amountCents(),
                input.date(),
                input.description(),
                TransactionStatus.SETTLED,
                pillar);

        return link(outgoing, incoming);
    }

    /**
     * Applies an edit to both legs. Shared facts (amount, date, description, note, status) are
     * mirrored; {@code accountId} only moves the leg being edited, since each side lives on its own
     * account. The contribution pillar is then re-derived from whichever account the IN leg now
     * points at: moving a contribution to a checking account drops the pillar, while moving it to
     * another investment account keeps the pillar already chosen (falling back to the new account's
     * default only when there is none) — a deliberate choice the user made is not silently
     * overwritten by a destination's default.
     */
    public Transaction update(UUID workspaceId, Transaction leg, TransactionInput input) {
        Transaction sibling = requireSibling(leg);
        if (input.accountId() != null && !input.accountId().equals(leg.getAccountId())) {
            Account account = TransactionLookup.ownedAccount(accountRepository, workspaceId, input.accountId());
            if (account.getId().equals(sibling.getAccountId())) {
                throw AppException.insufficientTransferAccounts();
            }
            leg.setAccountId(account.getId());
        }

        for (Transaction side : List.of(leg, sibling)) {
            side.setAmountCents(input.amountCents());
            side.setDate(input.date());
            side.setDescription(input.description());
            side.setNote(input.note());
            if (input.status() != null) {
                side.setStatus(input.status());
            }
        }

        Transaction incoming = leg.getTransferDirection() == TransferDirection.IN ? leg : sibling;
        Transaction outgoing = incoming == leg ? sibling : leg;
        Account destination = TransactionLookup.ownedAccount(accountRepository, workspaceId, incoming.getAccountId());
        // Only carry the current pillar forward while the destination still accepts one; otherwise
        // it must be dropped, not rejected — moving a contribution to a checking account is legal.
        ContributionPillar carried = isContributionTarget(destination) ? incoming.getContributionPillar() : null;
        outgoing.setContributionPillar(null);
        incoming.setContributionPillar(resolveContributionPillar(destination, carried));

        transactionRepository.saveAll(List.of(outgoing, incoming));
        return leg;
    }

    /** Settles both legs — a half-settled transfer would make one account's balance wrong. */
    public Transaction settle(Transaction leg) {
        Transaction sibling = requireSibling(leg);
        leg.setStatus(TransactionStatus.SETTLED);
        sibling.setStatus(TransactionStatus.SETTLED);
        transactionRepository.saveAll(List.of(leg, sibling));
        return leg;
    }

    /**
     * Deletes both legs. The two rows reference each other, so the mutual foreign key has to be
     * broken and flushed before either row can go.
     */
    public void delete(Transaction leg) {
        Transaction sibling = requireSibling(leg);
        leg.setTransferPairId(null);
        sibling.setTransferPairId(null);
        transactionRepository.saveAllAndFlush(List.of(leg, sibling));
        transactionRepository.deleteAll(List.of(leg, sibling));
        transactionRepository.flush();
    }

    /**
     * R9. Returns the pillar the destination account's contribution consumes, or null when the
     * destination is not a contribution target at all.
     */
    private ContributionPillar resolveContributionPillar(Account destination, ContributionPillar requested) {
        if (!isContributionTarget(destination)) {
            // A pillar on a plain checking/cash destination would silently inflate that pillar.
            if (requested != null) {
                throw AppException.invalidContributionPillar();
            }
            return null;
        }
        ContributionPillar resolved = requested != null
                ? requested
                : ContributionPillar.fromPillar(destination.getDefaultContributionPillar());
        if (resolved == null) {
            throw AppException.contributionPillarRequired();
        }
        return resolved;
    }

    /** R9 — only investment/reserve accounts receive contributions. */
    private boolean isContributionTarget(Account account) {
        return account.getType() == AccountType.INVESTMENT || account.getType() == AccountType.SAVINGS;
    }

    /**
     * Persists the two legs and makes them point at each other. The ids only exist after the
     * insert, so this is necessarily a three-step dance: insert the outgoing leg, insert the
     * incoming one pointing back at it, then close the loop.
     */
    private TransferPair link(Transaction outgoing, Transaction incoming) {
        transactionRepository.saveAndFlush(outgoing);
        incoming.setTransferPairId(outgoing.getId());
        transactionRepository.saveAndFlush(incoming);
        outgoing.setTransferPairId(incoming.getId());
        transactionRepository.saveAndFlush(outgoing);
        return new TransferPair(outgoing, incoming);
    }

    private Transaction requireSibling(Transaction leg) {
        if (leg.getTransferPairId() == null) {
            // Guaranteed by the DB (ck_transaction_pair) — reaching here means corrupted data.
            throw new IllegalStateException("Transfer leg " + leg.getId() + " has no pair");
        }
        return transactionRepository
                .findByIdAndWorkspaceId(leg.getTransferPairId(), leg.getWorkspaceId())
                .orElseThrow(() -> new IllegalStateException("Missing transfer pair for leg " + leg.getId()));
    }

    private Transaction leg(
            UUID workspaceId,
            UUID accountId,
            TransferDirection direction,
            long amountCents,
            LocalDate date,
            String description,
            TransactionStatus status,
            ContributionPillar contributionPillar) {
        return Transaction.builder()
                .workspaceId(workspaceId)
                .accountId(accountId)
                .categoryId(null)
                .type(TransactionType.TRANSFER)
                .transferDirection(direction)
                .amountCents(amountCents)
                .date(date)
                .description(description)
                .status(status)
                .contributionPillar(contributionPillar)
                .build();
    }
}
