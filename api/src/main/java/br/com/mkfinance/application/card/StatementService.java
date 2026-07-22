package br.com.mkfinance.application.card;

import br.com.mkfinance.application.transaction.TransactionService;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Card;
import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.domain.model.StatementPayment;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.infrastructure.persistence.CardRepository;
import br.com.mkfinance.infrastructure.persistence.InstallmentRepository;
import br.com.mkfinance.infrastructure.persistence.StatementItemRow;
import br.com.mkfinance.infrastructure.persistence.StatementPaymentRepository;
import br.com.mkfinance.infrastructure.persistence.StatementRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading statements and paying them (R2, §9.5).
 *
 * <p>The payment is the moment a credit-card purchase finally becomes money leaving an account. That
 * debit is created through BE-3's {@link TransactionService#createSettledExpense} rather than
 * inserted here, so it behaves like every other expense — it shows in the extrato, it moves the
 * account balance, and it obeys the same account/category validation — with the credit-card feature
 * owning only the bill it settles.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StatementService {

    /** How far back/forward the default listing window reaches when the client sends no range. */
    private static final int DEFAULT_WINDOW_MONTHS = 1;

    private final StatementRepository statementRepository;
    private final StatementPaymentRepository paymentRepository;
    private final InstallmentRepository installmentRepository;
    private final CardRepository cardRepository;
    private final CardService cardService;
    private final StatementProvider statementProvider;
    private final StatementTotalsLoader totalsLoader;
    private final StatementPaymentCategoryProvider paymentCategoryProvider;
    private final TransactionService transactionService;
    private final BusinessClock clock;

    /**
     * The card's statements in the requested window, materializing months that have none yet (R3).
     *
     * <p>With no window given, returns every statement the card already has plus the months around
     * today — so a fresh card is not an empty screen, while an old card does not silently create
     * years of blank rows just because someone opened the page.
     */
    // Not readOnly: listing materializes the statements of months that have none yet (R3), so this
    // read can legitimately write.
    @Transactional
    public List<StatementView> list(UUID workspaceId, UUID cardId, YearMonth from, YearMonth to) {
        Card card = cardService.get(workspaceId, cardId);
        List<Statement> statements = (from == null && to == null)
                ? defaultWindow(card)
                : statementProvider.statementsIn(card, from != null ? from : to, to != null ? to : from);
        return toViews(statements);
    }

    @Transactional(readOnly = true)
    public StatementView.Detail detail(UUID workspaceId, UUID statementId) {
        Statement statement = getStatement(workspaceId, statementId);
        List<StatementItemRow> items = installmentRepository.findItemsOfStatement(statementId);
        return new StatementView.Detail(toView(statement, totalsLoader.load(statementId)), items);
    }

    /**
     * Pays all or part of a statement (R2).
     *
     * <p>Refuses to overpay: the MVP has no notion of a credit balance on the card, so an amount
     * beyond what is owed is a mistake worth surfacing rather than absorbing. Anything short of the
     * full amount leaves the statement PARTIAL and the remainder simply outstanding — no revolving
     * interest is computed, by decision (§9.5).
     */
    public StatementView pay(UUID workspaceId, UUID statementId, StatementPaymentInput input) {
        Statement statement = getStatement(workspaceId, statementId);
        if (statement.isImmutable()) {
            throw AppException.statementAlreadyPaid();
        }

        StatementTotals totals = totalsLoader.load(statementId);
        if (totals.totalCents() <= 0) {
            throw AppException.statementEmpty();
        }
        long remaining = totals.remainingCents();
        if (input.amountCents() > remaining) {
            throw AppException.paymentExceedsRemaining(remaining);
        }

        Card card = cardRepository.findById(statement.getCardId()).orElseThrow(AppException::cardNotFound);
        // BE-3 owns money movement: this validates the account, books a SETTLED EXPENSE under the
        // system category reports exclude (R2), and hands back the id the payment row points at.
        Transaction expense = transactionService.createSettledExpense(
                workspaceId,
                input.accountId(),
                paymentCategoryProvider.categoryIdFor(workspaceId),
                input.amountCents(),
                input.paymentDate(),
                describe(card, statement));

        paymentRepository.save(StatementPayment.builder()
                .statementId(statement.getId())
                .accountId(input.accountId())
                .amountCents(input.amountCents())
                .paymentDate(input.paymentDate())
                .transactionId(expense.getId())
                .build());

        return toView(statement, totalsLoader.refreshStatus(statement));
    }

    @Transactional(readOnly = true)
    public Statement getStatement(UUID workspaceId, UUID statementId) {
        return statementRepository
                .findByIdAndWorkspaceId(statementId, workspaceId)
                .orElseThrow(AppException::statementNotFound);
    }

    /**
     * Description of the debit shown in the extrato, e.g. {@code "Nubank 2026-08"}. Built from data
     * (card name + reference month) rather than a localized literal — the backend never emits
     * pt-BR copy of its own (PLANEJAMENTO §3.3).
     */
    private String describe(Card card, Statement statement) {
        String description = card.getName() + " " + statement.referenceYearMonth();
        return description.length() <= 255 ? description : description.substring(0, 255);
    }

    private List<Statement> defaultWindow(Card card) {
        YearMonth current = YearMonth.from(clock.today());
        statementProvider.statementsIn(
                card, current.minusMonths(DEFAULT_WINDOW_MONTHS), current.plusMonths(DEFAULT_WINDOW_MONTHS));
        // The window is materialized now, so re-reading returns it together with the card's history
        // and any future statement an installment already reaches into.
        return statementProvider.existingStatements(card);
    }

    private List<StatementView> toViews(List<Statement> statements) {
        Map<UUID, StatementTotals> totals = totalsLoader.loadFor(statements);
        return statements.stream()
                .map(statement -> toView(statement, totals.getOrDefault(statement.getId(), StatementTotals.EMPTY)))
                .toList();
    }

    private StatementView toView(Statement statement, StatementTotals totals) {
        LocalDate today = clock.today();
        return new StatementView(
                statement, statement.effectiveStatus(today), totals.totalCents(), totals.paidCents());
    }
}
