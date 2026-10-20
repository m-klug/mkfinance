package br.com.mkfinance.application.card.statementimport;

import br.com.mkfinance.application.card.CardService;
import br.com.mkfinance.application.card.statementimport.ParsedStatement.CardTotal;
import br.com.mkfinance.application.card.statementimport.ParsedStatement.Kind;
import br.com.mkfinance.application.card.statementimport.ParsedStatement.ParsedLine;
import br.com.mkfinance.application.card.statementimport.StatementImportPreview.Candidate;
import br.com.mkfinance.application.card.statementimport.StatementImportPreview.Line;
import br.com.mkfinance.application.card.statementimport.StatementImportPreview.Status;
import br.com.mkfinance.application.card.statementimport.StatementImportPreview.Warning;
import br.com.mkfinance.domain.model.Card;
import br.com.mkfinance.domain.model.CardPurchase;
import br.com.mkfinance.infrastructure.persistence.CardPurchaseRepository;
import br.com.mkfinance.infrastructure.persistence.ImportCandidateRow;
import br.com.mkfinance.infrastructure.persistence.InstallmentRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an uploaded statement PDF into a reviewable preview. Writes nothing: the lines only become
 * purchases when the client confirms them through the batch endpoint.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatementImportService {

    public static final String WARNING_TOTAL_MISMATCH = "TOTAL_MISMATCH";
    public static final String WARNING_CARD_TOTAL_MISMATCH = "CARD_TOTAL_MISMATCH";
    public static final String WARNING_DUE_DATE_MISMATCH = "DUE_DATE_MISMATCH";
    public static final String WARNING_PAYMENT_IGNORED = "PAYMENT_IGNORED";

    /**
     * How far apart the date typed by hand and the date on the bill may be. The bank dates a line by
     * when the merchant posted it, which can trail the day the user actually paid.
     */
    private static final int MATCH_WINDOW_DAYS = 3;

    private static final Pattern INSTALLMENT_SUFFIX = Pattern.compile("\\s*\\(Parcela \\d{2} de \\d{2}\\)\\s*$");

    private final CardService cardService;
    private final CardPurchaseRepository purchaseRepository;
    private final InstallmentRepository installmentRepository;
    private final StatementPdfReader pdfReader;
    private final StatementParser parser;

    public StatementImportPreview preview(UUID workspaceId, UUID cardId, byte[] pdf, String password) {
        Card card = cardService.getActive(workspaceId, cardId);
        ParsedStatement statement = parser.parse(pdfReader.readText(pdf, password));

        List<Warning> warnings = new ArrayList<>();
        YearMonth statementMonth = statementMonthOf(card, statement);
        if (statement.dueDate().getDayOfMonth() != card.getDueDay()) {
            warnings.add(new Warning(WARNING_DUE_DATE_MISMATCH, null));
        }
        checkTotals(statement, warnings);

        Reconciler reconciler = new Reconciler(workspaceId, card, statement.lines());
        Map<String, UUID> suggestions = new HashMap<>();
        List<Line> lines = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (ParsedLine line : statement.lines()) {
            if (line.kind() == Kind.PAYMENT) {
                warnings.add(new Warning(WARNING_PAYMENT_IGNORED, line.cardLast4()));
                continue;
            }
            int occurrence = occurrences.merge(identityKey(line), 1, Integer::sum);
            String externalId = LineFingerprint.of(card.getId().toString(), line, occurrence);
            Reconciler.Outcome outcome = reconciler.reconcile(line, externalId);
            lines.add(new Line(
                    line.cardLast4(),
                    line.date(),
                    line.description(),
                    line.amountCents(),
                    line.kind(),
                    line.installmentNumber(),
                    line.installmentCount(),
                    outcome.status(),
                    outcome.status() == Status.ALREADY_IMPORTED,
                    externalId,
                    outcome.candidates(),
                    line.kind() == Kind.EXPENSE ? suggestion(workspaceId, line.description(), suggestions) : null));
        }
        return new StatementImportPreview(statementMonth, statement.dueDate(), statement.totalCents(), lines, warnings);
    }

    /** The statement closes the month before it is due unless the due day falls after the closing day. */
    private static YearMonth statementMonthOf(Card card, ParsedStatement statement) {
        YearMonth dueMonth = YearMonth.from(statement.dueDate());
        return card.getDueDay() > card.getClosingDay() ? dueMonth : dueMonth.minusMonths(1);
    }

    private static void checkTotals(ParsedStatement statement, List<Warning> warnings) {
        long expenses = expensesCents(statement.lines(), null);
        if (expenses != statement.expensesCents()) {
            warnings.add(new Warning(WARNING_TOTAL_MISMATCH, null));
        }
        for (CardTotal total : statement.cardTotals()) {
            if (expensesCents(statement.lines(), total.cardLast4()) != total.totalCents()) {
                warnings.add(new Warning(WARNING_CARD_TOTAL_MISMATCH, total.cardLast4()));
            }
        }
    }

    private static long expensesCents(List<ParsedLine> lines, String cardLast4) {
        return lines.stream()
                .filter(l -> l.kind() == Kind.EXPENSE)
                .filter(l -> cardLast4 == null || l.cardLast4().equals(cardLast4))
                .mapToLong(ParsedLine::amountCents)
                .sum();
    }

    private UUID suggestion(UUID workspaceId, String description, Map<String, UUID> cache) {
        String base = INSTALLMENT_SUFFIX.matcher(description).replaceFirst("");
        return cache.computeIfAbsent(base.toLowerCase(), k -> purchaseRepository
                .findFirstByWorkspaceIdAndDescriptionStartingWithIgnoreCaseOrderByPurchaseDateDesc(workspaceId, base)
                .map(CardPurchase::getCategoryId)
                .orElse(null));
    }

    /** Everything that makes two lines of the same file look identical; the occurrence index splits them. */
    private static String identityKey(ParsedLine line) {
        return String.join("|", line.cardLast4(), line.date().toString(), Long.toString(line.amountCents()),
                LineFingerprint.normalize(line.description()), String.valueOf(line.installmentNumber()),
                String.valueOf(line.installmentCount()));
    }

    /**
     * Decides, line by line, whether a bill line is new, already imported, or the bill's version of
     * something typed by hand. Description plays no part: the user's "Pastel com os amigos" and the
     * bank's "PASTELARIA ZEZINHO" are the same charge, and only amount, date and installment say so.
     * Each hand-typed installment is consumed by at most one line, so two identical lines on the bill
     * cannot both claim the single entry the user made.
     */
    private final class Reconciler {

        private final Set<String> alreadyImported;
        private final List<ImportCandidateRow> pool;
        private final Set<java.util.UUID> claimed = new HashSet<>();

        Reconciler(UUID workspaceId, Card card, List<ParsedLine> parsed) {
            LocalDate from = parsed.stream().map(ParsedLine::date).min(Comparator.naturalOrder())
                    .orElse(LocalDate.now()).minusDays(MATCH_WINDOW_DAYS);
            LocalDate to = parsed.stream().map(ParsedLine::date).max(Comparator.naturalOrder())
                    .orElse(LocalDate.now()).plusDays(MATCH_WINDOW_DAYS);
            this.pool = installmentRepository.findImportCandidates(workspaceId, card.getId(), from, to);
            this.alreadyImported = new HashSet<>();
            pool.stream().map(ImportCandidateRow::externalId).filter(java.util.Objects::nonNull)
                    .forEach(alreadyImported::add);
        }

        Outcome reconcile(ParsedLine line, String externalId) {
            if (alreadyImported.contains(externalId)) {
                return new Outcome(Status.ALREADY_IMPORTED, List.of());
            }
            // Credits have no counterpart on the card side (the import skips them), so nothing to match.
            if (line.kind() != Kind.EXPENSE) {
                return new Outcome(Status.NEW, List.of());
            }
            short number = (short) (line.installmentNumber() == null ? 1 : line.installmentNumber());
            short count = (short) (line.installmentCount() == null ? 1 : line.installmentCount());
            List<ImportCandidateRow> fits = pool.stream()
                    .filter(row -> row.externalId() == null)
                    .filter(row -> !claimed.contains(row.installmentId()))
                    .filter(row -> row.amountCents() == line.amountCents())
                    .filter(row -> row.number() == number && row.installmentCount() == count)
                    .filter(row -> Math.abs(ChronoUnit.DAYS.between(row.purchaseDate(), line.date())) <= MATCH_WINDOW_DAYS)
                    .sorted(Comparator.comparingLong(
                            row -> Math.abs(ChronoUnit.DAYS.between(row.purchaseDate(), line.date()))))
                    .toList();
            if (fits.isEmpty()) {
                return new Outcome(Status.NEW, List.of());
            }
            List<Candidate> candidates = fits.stream()
                    .map(row -> new Candidate(row.installmentId(), row.description(), row.purchaseDate()))
                    .toList();
            if (fits.size() == 1) {
                claimed.add(fits.get(0).installmentId());
                return new Outcome(Status.MATCH, candidates);
            }
            return new Outcome(Status.AMBIGUOUS, candidates);
        }

        record Outcome(Status status, List<Candidate> candidates) {
        }
    }
}
