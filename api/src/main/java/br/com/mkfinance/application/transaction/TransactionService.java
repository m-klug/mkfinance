package br.com.mkfinance.application.transaction;

import br.com.mkfinance.application.recurrence.RecurrenceOccurrenceMaterializer;
import br.com.mkfinance.application.tag.TagService;
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
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Set;
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

    /**
     * Ceiling on how many competencies a single extrato read may materialize (BE-8, decision
     * 30/07/2026). About a year of browsing — generous enough for normal use, but bounded, so a
     * client asking for an unrestricted or absurdly wide window cannot make this write rows "until
     * 2094", the very thing the contract's {@code createRecurrence} already guards against. A window
     * wider than this — or with {@code from}/{@code to} missing altogether — materializes nothing and
     * only reads what already exists.
     */
    private static final int MAX_MATERIALIZED_MONTHS = 12;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransferService transferService;
    private final TagService tagService;
    private final RecurrenceOccurrenceMaterializer recurrenceMaterializer;

    /**
     * The extrato listing: optional filters, newest first, paginated (contract: TransactionPage).
     *
     * <p>The ordering is imposed here rather than taken from the caller's {@code Pageable}, so no
     * endpoint can accidentally page through an unordered result — which in Postgres would repeat
     * and skip rows across pages. {@code createdAt} and {@code id} break ties on equal dates to keep
     * paging stable.
     *
     * <p>Before querying, materializes the recurrence occurrences of every competency
     * {@code filter.from()}/{@code filter.to()} touches (see {@link #materializeWindow}) — reusing
     * {@link RecurrenceOccurrenceMaterializer}, the same component the calendar already calls, so a
     * recurrence shows up in the extrato without the user having to open the calendar first.
     */
    public Page<Transaction> list(UUID workspaceId, TransactionFilter filter, Pageable pageable) {
        materializeWindow(workspaceId, filter.from(), filter.to());
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        Set<UUID> tagTransactionIds = tagService.matchingTransactionIds(workspaceId, filter.tags());
        return transactionRepository.findAll(TransactionSpecifications.of(workspaceId, filter, tagTransactionIds), ordered);
    }

    /**
     * Materializes every competency between {@code from} and {@code to}, inclusive, when the window
     * is bounded and no wider than {@link #MAX_MATERIALIZED_MONTHS}. A missing bound, an inverted
     * range, or a too-wide one is treated as "unrestricted" and materializes nothing — idempotent
     * either way, since {@link RecurrenceOccurrenceMaterializer} itself never duplicates a
     * transaction for a competency already read.
     */
    private void materializeWindow(UUID workspaceId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return;
        }
        YearMonth start = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        long spanMonths = ChronoUnit.MONTHS.between(start, end) + 1;
        if (spanMonths > MAX_MATERIALIZED_MONTHS) {
            return;
        }
        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            recurrenceMaterializer.materializeMonth(workspaceId, month);
        }
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
     *
     * <p>{@code input.tags()} resolves through {@link TagService#resolveOrCreate} — names that do not
     * exist yet are created on the spot (CT-1) — and {@code null} means no tags on a brand-new entry
     * (there is nothing to "leave alone" yet).
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
        transaction = transactionRepository.save(transaction);
        if (input.tags() != null) {
            tagService.replaceTransactionTags(transaction.getId(), tagService.resolveOrCreate(workspaceId, input.tags()));
        }
        return transaction;
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
            // This endpoint has no pillar field, so a transfer edited here keeps whichever pillar it
            // already carries (null = "leave alone"); re-targeting a contribution goes through
            // PATCH /transfers. The reload inside the engine is a persistence-context hit, no SQL.
            TransferUpdateInput transferInput = new TransferUpdateInput(
                    input.accountId(),
                    input.amountCents(),
                    input.date(),
                    input.description(),
                    input.note(),
                    input.status(),
                    null);
            return transferService.update(workspaceId, transactionId, transferInput).legById(transactionId);
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
        transaction = transactionRepository.save(transaction);
        // null means "leave the current tags alone" (contract); a present list, even empty, replaces
        // the whole set.
        if (input.tags() != null) {
            tagService.replaceTransactionTags(transaction.getId(), tagService.resolveOrCreate(workspaceId, input.tags()));
        }
        return transaction;
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
                        TransactionStatus.SETTLED,
                        null));
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
