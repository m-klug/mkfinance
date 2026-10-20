package br.com.mkfinance.application.card.statementimport;

import java.time.LocalDate;
import java.util.List;

/**
 * What a {@link StatementParser} read out of a statement's text, before any of it is matched to the
 * database.
 *
 * @param totalCents the bill total printed on the first page
 * @param expensesCents the "monthly expenses" figure, used to cross-check the parsed lines
 * @param cardTotals the {@code Total CARTÃO ...} figure printed under each card's table
 */
public record ParsedStatement(
        LocalDate dueDate, long totalCents, long expensesCents, List<ParsedLine> lines, List<CardTotal> cardTotals) {

    /** How a line moves the bill. */
    public enum Kind {
        /** A charge: adds to the bill. */
        EXPENSE,
        /** A credit that is not a bill payment, typically a refund. */
        CREDIT,
        /** The payment of the previous bill; not a purchase and never imported. */
        PAYMENT
    }

    public record ParsedLine(
            String cardLast4,
            LocalDate date,
            String description,
            long amountCents,
            Kind kind,
            Integer installmentNumber,
            Integer installmentCount) {
    }

    public record CardTotal(String cardLast4, long totalCents) {
    }
}
