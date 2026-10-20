package br.com.mkfinance.application.account.statementimport;

import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Duplicate;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Kind;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Line;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Mode;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.StatementCandidate;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Warning;
import br.com.mkfinance.application.account.statementimport.ParsedBankStatement.ParsedBankTransaction;
import br.com.mkfinance.application.card.StatementPaymentInput;
import br.com.mkfinance.application.card.StatementService;
import br.com.mkfinance.application.card.StatementTotals;
import br.com.mkfinance.application.card.StatementTotalsLoader;
import br.com.mkfinance.application.transaction.AccountBalanceService;
import br.com.mkfinance.application.transaction.TransactionInput;
import br.com.mkfinance.application.transaction.TransactionService;
import br.com.mkfinance.application.transaction.TransferInput;
import br.com.mkfinance.application.transaction.TransferService;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.Card;
import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.CardRepository;
import br.com.mkfinance.infrastructure.persistence.StatementRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an uploaded bank statement into a reviewable preview, and the confirmed lines into records.
 * The preview writes nothing; {@link #confirm} writes everything or nothing.
 *
 * <p>Every record is created through the service that already owns that rule — entries through
 * {@link TransactionService}, transfers through {@link TransferService} (R4, R9), card bill
 * payments through {@link StatementService#pay} (R2) — so an imported line obeys exactly what the
 * same line typed by hand would.
 */
@Service
@RequiredArgsConstructor
public class BankStatementImportService {

    public static final String WARNING_ZERO_AMOUNT_IGNORED = "ZERO_AMOUNT_IGNORED";
    public static final String WARNING_STATEMENT_PAYMENT_UNMATCHED = "STATEMENT_PAYMENT_UNMATCHED";

    /** A card bill debit lands on, or a few days around, the statement's due date. */
    private static final int STATEMENT_MATCH_DAYS = 5;
    private static final int MAX_DESCRIPTION = 255;

    private static final Pattern PIX_WITH_ID = Pattern.compile("^(Pix [^:]+): \"Cp :\\d+-(.*)\"$");
    private static final Pattern QUOTED_VALUE = Pattern.compile("^([^:]+): \"(.*)\"$");

    private final BankStatementParser parser;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final StatementRepository statementRepository;
    private final CardRepository cardRepository;
    private final StatementTotalsLoader totalsLoader;
    private final AccountBalanceService balanceService;
    private final TransactionService transactionService;
    private final TransferService transferService;
    private final StatementService statementService;

    @Transactional(readOnly = true)
    public BankStatementImportPreview preview(UUID workspaceId, UUID accountId, byte[] file) {
        Account account = ownedAccount(workspaceId, accountId);
        ParsedBankStatement statement = parser.parse(file);

        List<Warning> warnings = new ArrayList<>();
        List<ParsedBankTransaction> usable = new ArrayList<>();
        for (ParsedBankTransaction transaction : statement.transactions()) {
            if (transaction.amountCents() == 0) {
                warnings.add(new Warning(WARNING_ZERO_AMOUNT_IGNORED, transaction.externalId()));
            } else {
                usable.add(transaction);
            }
        }

        Set<String> known = knownExternalIds(account, usable);
        Map<String, Integer> byHand = byHandCounts(workspaceId, account, usable);
        UUID investmentAccountId = onlyInvestmentAccount(workspaceId);
        List<StatementCandidate> openStatements = openStatements(workspaceId, usable);
        Map<String, UUID> categorySuggestions = new HashMap<>();

        int unmatchedPayments = 0;
        List<Line> lines = new ArrayList<>();
        for (ParsedBankTransaction transaction : usable) {
            TransactionType type = transaction.amountCents() > 0 ? TransactionType.INCOME : TransactionType.EXPENSE;
            long amount = Math.abs(transaction.amountCents());
            String description = describe(transaction);
            Kind kind = kindOf(transaction.memo(), type);
            Duplicate duplicate = duplicateOf(transaction, known, byHand);

            Mode mode = Mode.ENTRY;
            UUID suggestedCounterpart = null;
            UUID suggestedStatement = null;
            List<StatementCandidate> candidates = List.of();
            boolean selected = duplicate == Duplicate.NONE;

            switch (kind) {
                case INVESTMENT_OUT, INVESTMENT_IN -> {
                    mode = Mode.TRANSFER;
                    suggestedCounterpart = investmentAccountId;
                    selected &= investmentAccountId != null;
                }
                case CARD_BILL_PAYMENT -> {
                    mode = Mode.STATEMENT_PAYMENT;
                    candidates = candidatesNear(openStatements, transaction.date());
                    List<StatementCandidate> exact =
                            candidates.stream().filter(c -> c.remainingCents() == amount).toList();
                    if (exact.size() == 1) {
                        suggestedStatement = exact.get(0).statementId();
                    } else {
                        selected = false;
                        unmatchedPayments++;
                    }
                }
                case NORMAL -> {
                }
            }

            UUID suggestedCategory = mode == Mode.ENTRY ? categorySuggestion(workspaceId, type, description, categorySuggestions) : null;
            lines.add(new Line(
                    transaction.externalId(),
                    transaction.date(),
                    description,
                    type,
                    amount,
                    kind,
                    mode,
                    duplicate,
                    suggestedCategory,
                    suggestedCounterpart,
                    suggestedStatement,
                    candidates,
                    selected));
        }
        if (unmatchedPayments > 0) {
            warnings.add(new Warning(WARNING_STATEMENT_PAYMENT_UNMATCHED, String.valueOf(unmatchedPayments)));
        }

        return new BankStatementImportPreview(
                statement.bankId(),
                last4(statement.accountId()),
                statement.periodStart(),
                statement.periodEnd(),
                balanceService.balanceOf(workspaceId, accountId).currentCents(),
                statement.ledgerBalanceCents(),
                statement.ledgerBalanceDate(),
                lines,
                warnings);
    }

    /** All-or-nothing: one refused line (paid statement, wrong category type, ...) records none. */
    @Transactional
    public BankStatementImportResult confirm(UUID workspaceId, UUID accountId, List<BankStatementImportItem> items) {
        Account account = ownedAccount(workspaceId, accountId);

        Set<String> ids = new HashSet<>();
        for (BankStatementImportItem item : items) {
            if (item.externalId() == null || item.externalId().isBlank() || !ids.add(item.externalId())) {
                throw AppException.ofxItemAlreadyImported(String.valueOf(item.externalId()));
            }
        }
        for (String existing : transactionRepository.findExistingExternalIds(account.getId(), ids)) {
            throw AppException.ofxItemAlreadyImported(existing);
        }

        int entries = 0;
        int transfers = 0;
        int payments = 0;
        for (BankStatementImportItem item : items) {
            switch (item.mode()) {
                case ENTRY -> {
                    requireComplete(item, item.categoryId() != null);
                    transactionService.create(workspaceId, new TransactionInput(
                            account.getId(),
                            item.categoryId(),
                            item.type(),
                            item.amountCents(),
                            item.date(),
                            item.description(),
                            null,
                            TransactionStatus.SETTLED,
                            null,
                            item.externalId()));
                    entries++;
                }
                case TRANSFER -> {
                    requireComplete(item, item.counterpartAccountId() != null);
                    boolean incoming = item.type() == TransactionType.INCOME;
                    transferService.create(workspaceId, new TransferInput(
                            incoming ? item.counterpartAccountId() : account.getId(),
                            incoming ? account.getId() : item.counterpartAccountId(),
                            item.amountCents(),
                            item.date(),
                            item.description(),
                            incoming ? null : item.contributionPillar(),
                            null,
                            null,
                            item.externalId(),
                            account.getId()));
                    transfers++;
                }
                case STATEMENT_PAYMENT -> {
                    requireComplete(item, item.statementId() != null && item.type() == TransactionType.EXPENSE);
                    statementService.pay(workspaceId, item.statementId(), new StatementPaymentInput(
                            account.getId(), item.amountCents(), item.date(), item.externalId()));
                    payments++;
                }
            }
        }
        return new BankStatementImportResult(entries, transfers, payments);
    }

    private static void requireComplete(BankStatementImportItem item, boolean complete) {
        if (!complete || item.type() == null || item.type() == TransactionType.TRANSFER || item.amountCents() <= 0) {
            throw AppException.ofxItemIncomplete(item.externalId());
        }
    }

    private Account ownedAccount(UUID workspaceId, UUID accountId) {
        return accountRepository.findByIdAndWorkspaceId(accountId, workspaceId).orElseThrow(AppException::accountNotFound);
    }

    private Set<String> knownExternalIds(Account account, List<ParsedBankTransaction> transactions) {
        if (transactions.isEmpty()) {
            return Set.of();
        }
        List<String> ids = transactions.stream().map(ParsedBankTransaction::externalId).toList();
        return new HashSet<>(transactionRepository.findExistingExternalIds(account.getId(), ids));
    }

    /**
     * Multiset of the account's hand-typed rows inside the file's period, keyed by day, signed amount.
     * Each one explains at most one line of the file.
     */
    private Map<String, Integer> byHandCounts(UUID workspaceId, Account account, List<ParsedBankTransaction> transactions) {
        Map<String, Integer> counts = new HashMap<>();
        if (transactions.isEmpty()) {
            return counts;
        }
        LocalDate from = transactions.stream().map(ParsedBankTransaction::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = transactions.stream().map(ParsedBankTransaction::date).max(Comparator.naturalOrder()).orElseThrow();
        for (Transaction row : transactionRepository.findByAccountIdAndWorkspaceIdAndExternalIdIsNullAndDateBetween(
                account.getId(), workspaceId, from, to)) {
            counts.merge(key(row.getDate(), signed(row)), 1, Integer::sum);
        }
        return counts;
    }

    private static long signed(Transaction row) {
        boolean incoming = row.getType() == TransactionType.INCOME
                || (row.getType() == TransactionType.TRANSFER && row.getTransferDirection() == TransferDirection.IN);
        return incoming ? row.getAmountCents() : -row.getAmountCents();
    }

    private static String key(LocalDate date, long signedCents) {
        return date + "|" + signedCents;
    }

    private static Duplicate duplicateOf(
            ParsedBankTransaction transaction, Set<String> known, Map<String, Integer> byHand) {
        if (known.contains(transaction.externalId())) {
            return Duplicate.EXACT;
        }
        String key = key(transaction.date(), transaction.amountCents());
        int remaining = byHand.getOrDefault(key, 0);
        if (remaining == 0) {
            return Duplicate.NONE;
        }
        byHand.put(key, remaining - 1);
        return Duplicate.POSSIBLE;
    }

    /** The one investment account of the workspace, or null when there are none or several. */
    private UUID onlyInvestmentAccount(UUID workspaceId) {
        List<Account> investments = accountRepository.findByWorkspaceIdAndArchivedFalseOrderByNameAsc(workspaceId).stream()
                .filter(a -> a.getType() == AccountType.INVESTMENT)
                .toList();
        return investments.size() == 1 ? investments.get(0).getId() : null;
    }

    /** Statements with something still owed that are due around any of the file's card bill debits. */
    private List<StatementCandidate> openStatements(UUID workspaceId, List<ParsedBankTransaction> transactions) {
        List<LocalDate> billDates = transactions.stream()
                .filter(t -> t.amountCents() < 0 && kindOf(t.memo(), TransactionType.EXPENSE) == Kind.CARD_BILL_PAYMENT)
                .map(ParsedBankTransaction::date)
                .toList();
        if (billDates.isEmpty()) {
            return List.of();
        }
        LocalDate from = billDates.stream().min(Comparator.naturalOrder()).orElseThrow().minusDays(STATEMENT_MATCH_DAYS);
        LocalDate to = billDates.stream().max(Comparator.naturalOrder()).orElseThrow().plusDays(STATEMENT_MATCH_DAYS);
        List<Statement> statements = statementRepository.findByWorkspaceIdAndDueDateBetween(workspaceId, from, to);
        if (statements.isEmpty()) {
            return List.of();
        }
        Map<UUID, StatementTotals> totals =
                totalsLoader.load(statements.stream().map(Statement::getId).toList());
        Map<UUID, Card> cards = cardRepository
                .findAllById(statements.stream().map(Statement::getCardId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Card::getId, c -> c));
        List<StatementCandidate> candidates = new ArrayList<>();
        for (Statement statement : statements) {
            long remaining = totals.getOrDefault(statement.getId(), StatementTotals.EMPTY).remainingCents();
            Card card = cards.get(statement.getCardId());
            if (remaining > 0 && card != null) {
                candidates.add(new StatementCandidate(
                        statement.getId(),
                        card.getId(),
                        card.getName(),
                        statement.referenceYearMonth(),
                        statement.getDueDate(),
                        remaining));
            }
        }
        return candidates;
    }

    private static List<StatementCandidate> candidatesNear(List<StatementCandidate> open, LocalDate date) {
        return open.stream()
                .filter(c -> Math.abs(c.dueDate().toEpochDay() - date.toEpochDay()) <= STATEMENT_MATCH_DAYS)
                .toList();
    }

    private UUID categorySuggestion(UUID workspaceId, TransactionType type, String description, Map<String, UUID> cache) {
        return cache.computeIfAbsent(type + "|" + description.toLowerCase(Locale.ROOT), k -> transactionRepository
                .findFirstByWorkspaceIdAndTypeAndDescriptionIgnoreCaseAndCategoryIdIsNotNullOrderByDateDescCreatedAtDesc(
                        workspaceId, type, description)
                .map(Transaction::getCategoryId)
                .orElse(null));
    }

    /** Classifies by what the bank wrote; only chooses the line's default mode. */
    static Kind kindOf(String memo, TransactionType type) {
        String text = Normalizer.normalize(memo == null ? "" : memo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .strip();
        if (type == TransactionType.EXPENSE && text.contains("fatura cartao")) {
            return Kind.CARD_BILL_PAYMENT;
        }
        if (type == TransactionType.EXPENSE && text.startsWith("aplicacao")) {
            return Kind.INVESTMENT_OUT;
        }
        if (type == TransactionType.INCOME && (text.startsWith("vencimento renda fixa") || text.startsWith("resgate"))) {
            return Kind.INVESTMENT_IN;
        }
        return Kind.NORMAL;
    }

    /**
     * Description shown and stored: the bank's memo without its noise (quotes around the value, runs
     * of spaces, the numeric id the Pix memo repeats), falling back to the counterpart's name.
     */
    static String describe(ParsedBankTransaction transaction) {
        String memo = transaction.memo() == null ? "" : transaction.memo().strip();
        Matcher pix = PIX_WITH_ID.matcher(memo);
        String text;
        if (pix.matches()) {
            String who = transaction.name() != null && !transaction.name().isBlank() ? transaction.name() : pix.group(2);
            text = pix.group(1) + " - " + who;
        } else {
            Matcher quoted = QUOTED_VALUE.matcher(memo);
            text = quoted.matches() ? quoted.group(1) + ": " + quoted.group(2) : memo;
        }
        text = text.replaceAll("\\s+", " ").strip();
        if (text.isEmpty()) {
            text = transaction.name() != null && !transaction.name().isBlank() ? transaction.name().strip() : transaction.externalId();
        }
        return text.length() <= MAX_DESCRIPTION ? text : text.substring(0, MAX_DESCRIPTION);
    }

    private static String last4(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }
        String digits = accountId.strip();
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }
}
