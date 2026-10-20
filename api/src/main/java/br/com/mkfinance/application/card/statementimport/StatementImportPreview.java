package br.com.mkfinance.application.card.statementimport;

import br.com.mkfinance.application.card.statementimport.ParsedStatement.Kind;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * What the user reviews before anything is written: the lines of the PDF matched against what the
 * workspace already has.
 *
 * @param statementMonth the statement every line will be charged to (decided by the bill's due date)
 * @param warnings machine codes the client translates; none of them blocks the import
 */
public record StatementImportPreview(
        YearMonth statementMonth,
        LocalDate dueDate,
        long totalCents,
        List<Line> lines,
        List<Warning> warnings) {

    /**
     * @param kind {@code EXPENSE} or {@code CREDIT}; payments of the previous bill never get here
     * @param status what the importer decided about the line; see {@link Status}
     * @param duplicate shorthand for {@code status == ALREADY_IMPORTED}
     * @param externalId fingerprint of the line, echoed back when the line is confirmed
     * @param candidates hand-typed installments the line may be; one for {@code MATCH}, several for
     *     {@code AMBIGUOUS}, empty otherwise
     * @param suggestedCategoryId category of the latest purchase with the same description, or null
     */
    public record Line(
            String cardLast4,
            LocalDate date,
            String description,
            long amountCents,
            Kind kind,
            Integer installmentNumber,
            Integer installmentCount,
            Status status,
            boolean duplicate,
            String externalId,
            List<Candidate> candidates,
            UUID suggestedCategoryId) {
    }

    public enum Status {
        /** Nothing like it on the card: importing creates it. */
        NEW,
        /** Exactly one hand-typed entry has the same card, amount, installment and a date within the window. */
        MATCH,
        /** More than one hand-typed entry fits; the user picks which one the line is, or none. */
        AMBIGUOUS,
        /** This very line was imported (or linked) before. */
        ALREADY_IMPORTED
    }

    /** A hand-typed installment a line may be the bill's version of. */
    public record Candidate(UUID installmentId, String description, LocalDate purchaseDate) {
    }

    /** @param detail what the warning is about (a card's last four digits), or null */
    public record Warning(String code, String detail) {
    }
}
