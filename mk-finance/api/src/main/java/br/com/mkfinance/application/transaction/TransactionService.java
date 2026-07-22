package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.Category;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.CategoryRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point for every transaction mutation (PLANEJAMENTO §6.3).
 *
 * <p>Handles INCOME/EXPENSE itself and delegates anything touching a transfer leg to
 * {@link TransferService}, so the mirrored pair (R4) stays consistent no matter which endpoint the
 * caller used — editing, settling or deleting one leg always moves both.
 *
 * <p><strong>Internal API for BE-4 (credit card).</strong> Paying a statement debits an account,
 * and that debit must be a normal transaction so it shows in the extrato and in the account
 * balance. BE-4 creates it through {@link #createSettledExpense} instead of inserting rows itself
 * — that method is the contract between the two workstreams.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TransactionService {

    /** Contract: "Paginated transactions, newest first." */
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("date"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransferService transferService;

    /**
     * The extrato listing: optional filters, newest first, paginated (contract: TransactionPage).
     *
     * <p>The ordering is imposed here rather than taken from the caller's {@code Pageable}, so no
     * endpoint can accidentally page through an unordered result — which in Postgres would repeat
     * and skip rows across pages. {@code createdAt} and {@code id} break ties on equal dates to keep
     * paging stable.
     */
    @Transactional(readOnly = true)
    public Page<Transaction> list(UUID workspaceId, TransactionFilter filter, Pageable pageable) {
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        return transactionRepository.findAll(TransactionSpecifications.of(workspaceId, filter), ordered);
    }

    @Transactional(readOnly = true)
    public Transaction get(UUID workspaceId, UUID transactionId) {
        return transactionRepository
                .findByIdAndWorkspaceId(transactionId, workspaceId)
                .orElseThrow(AppException::transactionNotFound);
    }

    /**
     * Creates an INCOME or EXPENSE entry. Transfers do not come through here — they need two legs,
     * so they go through {@link TransferService#create} behind the {@code /transfers} endpoint.
     */
    public Transaction create(UUID workspaceId, TransactionInput input) {
        TransactionType type = requireEntryType(input.type());
        Account account = TransactionLookup.ownedAccount(accountRepository, workspaceId, input.accountId());
        Category category = requireMatchingCategory(workspaceId, input.categoryId(), type);

        Transaction transaction = Transaction.builder()
                .workspaceId(workspaceId)
                .accountId(account.getId())
                .categoryId(category.getId())
                .type(type)
                .amountCents(input.amountCents())
                .date(input.date())
                .description(input.description())
                .note(input.note())
                .status(input.status() != null ? input.status() : TransactionStatus.SETTLED)
                .build();
        return transactionRepository.save(transaction);
    }

    /**
     * Edits a transaction. For a transfer leg the request's {@code type} and {@code categoryId} are
     * ignored (a transfer has neither a category nor a changeable type) and the edit is mirrored
     * onto the sibling leg; converting an entry into a transfer, or the reverse, is refused with
     * TRANSACTION_TYPE_IMMUTABLE.
     */
    public Transaction update(UUID workspaceId, UUID transactionId, TransactionInput input) {
        Transaction transaction = get(workspaceId, transactionId);

        if (transaction.isTransfer()) {
            return transferService.update(workspaceId, transaction, input);
        }
        if (input.type() == TransactionType.TRANSFER) {
            throw AppException.transactionTypeImmutable();
        }

        TransactionType type = requireEntryType(input.type());
        Account account = TransactionLookup.ownedAccount(accountRepository, workspaceId, input.accountId());
        Category category = requireMatchingCategory(workspaceId, input.categoryId(), type);

        transaction.setAccountId(account.getId());
        transaction.setCategoryId(category.getId());
        transaction.setType(type);
        transaction.setAmountCents(input.amountCents());
        transaction.setDate(input.date());
        transaction.setDescription(input.description());
        transaction.setNote(input.note());
        if (input.status() != null) {
            transaction.setStatus(input.status());
        }
        return transactionRepository.save(transaction);
    }

    /**
     * Deleting one leg of a transfer deletes its pair too (contract), never leaving a half-transfer.
     *
     * <p>A statement payment's debit is refused instead: the row is still referenced by
     * {@code statement_payment}, and dropping it would leave a bill claiming to be paid with no money
     * having moved. The guard is asked before the delete so the client gets
     * TRANSACTION_LOCKED_BY_PAYMENT — which names the way out (reverse it on the statement) — rather
     * than the foreign key's opaque DATA_INTEGRITY_VIOLATION. The statement reversal itself deletes
     * the payment row first, so by the time it calls this the guard no longer matches.
     */
    public void delete(UUID workspaceId, UUID transactionId) {
        Transaction transaction = get(workspaceId, transactionId);
        if (transactionRepository.existsStatementPaymentForTransaction(transaction.getId())) {
            throw AppException.transactionLockedByPayment();
        }
        if (transaction.isTransfer()) {
            transferService.delete(transaction);
            return;
        }
        transactionRepository.delete(transaction);
    }

    /**
     * One-click settle of a scheduled entry (R5) — the "efetivar" action in the extrato. Settling an
     * already-settled transaction is a conflict rather than a no-op, so a double click cannot be
     * mistaken for a second payment.
     */
    public Transaction settle(UUID workspaceId, UUID transactionId) {
        Transaction transaction = get(workspaceId, transactionId);
        if (transaction.getStatus() == TransactionStatus.SETTLED) {
            throw AppException.transactionAlreadySettled();
        }
        if (transaction.isTransfer()) {
            return transferService.settle(transaction);
        }
        transaction.setStatus(TransactionStatus.SETTLED);
        return transactionRepository.save(transaction);
    }

    /**
     * Creates a settled expense on an account — the internal API BE-4 uses to debit the payment
     * account when a credit-card statement is paid (R2), instead of inserting into {@code
     * transaction} directly.
     *
     * <p>Validates that both account and category belong to {@code workspaceId} (R8) and that the
     * category is an EXPENSE one; the returned entity already has its generated id, which is what
     * {@code statement_payment.transaction_id} stores.
     *
     * @param categoryId the system "statement payment" category ({@code system = true}), which
     *     reports by category exclude (R2)
     * @param amountCents amount in cents, always positive
     * @param date business date in America/Sao_Paulo (R7)
     * @return the persisted, SETTLED expense
     */
    public Transaction createSettledExpense(
            UUID workspaceId,
            UUID accountId,
            UUID categoryId,
            long amountCents,
            LocalDate date,
            String description) {
        return create(
                workspaceId,
                new TransactionInput(
                        accountId,
                        categoryId,
                        TransactionType.EXPENSE,
                        amountCents,
                        date,
                        description,
                        null,
                        TransactionStatus.SETTLED));
    }

    /** The transactions endpoint only accepts INCOME/EXPENSE — transfers need the /transfers one. */
    private TransactionType requireEntryType(TransactionType type) {
        if (type == null || type == TransactionType.TRANSFER) {
            throw AppException.transactionTypeImmutable();
        }
        return type;
    }

    /**
     * An EXPENSE must be classified by an EXPENSE category and an INCOME by an INCOME one —
     * otherwise a mismatched row would land in the wrong pillar and quietly corrupt the allocation
     * report (R10).
     */
    private Category requireMatchingCategory(UUID workspaceId, UUID categoryId, TransactionType type) {
        Category category = TransactionLookup.ownedCategory(categoryRepository, workspaceId, categoryId);
        CategoryType expected = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        if (category.getType() != expected) {
            throw AppException.categoryTypeMismatch();
        }
        return category;
    }
}
