package br.com.mkfinance.application.card.statementimport;

import br.com.mkfinance.application.card.statementimport.ParsedStatement.CardTotal;
import br.com.mkfinance.application.card.statementimport.ParsedStatement.Kind;
import br.com.mkfinance.application.card.statementimport.ParsedStatement.ParsedLine;
import br.com.mkfinance.domain.exception.AppException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Banco Inter credit-card statement, as extracted from the PDF text.
 *
 * <p>The layout is fixed: a header with the bill's due date and total, then one table per physical
 * or virtual card, each introduced by {@code CARTÃO 5364****5576} and closed by
 * {@code Total CARTÃO 5364****5576 R$ 3.080,96}. A table row reads
 * {@code 22 de jul. 2026 MERCADOLIVRE*MERCADOL (Parcela 02 de 02) - R$ 239,99}, with {@code + R$}
 * marking a credit. Anything that matches none of those shapes — page headers, boilerplate, the
 * boleto — is ignored rather than rejected, so a layout tweak outside the tables does not break the
 * import.
 */
@Component
public class InterStatementParser implements StatementParser {

    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private static final List<String> MONTHS =
            List.of("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez");

    /**
     * The strip repeated at the top of every page after the first: {@code 5364****2148 10/09/2026 R$ 3.111,96}
     * (card, due date, bill total). Read from here rather than from the labelled summary on page 1,
     * because the PDF text extractors disagree on how that page's label/value pairs are ordered.
     */
    private static final Pattern PAGE_STRIP =
            Pattern.compile("^\\d{4}\\*+\\d{4}\\s+(\\d{2}/\\d{2}/\\d{4})\\s+R\\$\\s*([\\d.]+,\\d{2})$", Pattern.MULTILINE);
    private static final Pattern EXPENSES = Pattern.compile("Despesas do mês\\s+R\\$\\s*([\\d.]+,\\d{2})");
    private static final Pattern CARD_HEADER = Pattern.compile("^CARTÃO\\s+\\d{4}\\*+(\\d{4})$");
    private static final Pattern CARD_TOTAL = Pattern.compile("^Total CARTÃO\\s+\\d{4}\\*+(\\d{4})\\s+R\\$\\s*([\\d.]+,\\d{2})$");
    private static final Pattern LINE = Pattern.compile(
            "^(\\d{2}) de ([a-zç]{3})\\.? (\\d{4}) (.+?) - (\\+ )?R\\$\\s*([\\d.]+,\\d{2})$");
    private static final Pattern INSTALLMENT = Pattern.compile("\\(Parcela (\\d{2}) de (\\d{2})\\)");
    private static final String PAYMENT_MARKER = "PAGTO DEBITO AUTOMATICO";

    @Override
    public ParsedStatement parse(String text) {
        Matcher strip = PAGE_STRIP.matcher(text);
        if (!strip.find()) {
            throw AppException.statementPdfUnsupported();
        }
        Matcher expenses = EXPENSES.matcher(text);
        long expensesCents = expenses.find() ? cents(expenses.group(1)) : cents(strip.group(2));

        List<ParsedLine> lines = new ArrayList<>();
        List<CardTotal> cardTotals = new ArrayList<>();
        String currentCard = null;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            Matcher header = CARD_HEADER.matcher(line);
            if (header.matches()) {
                currentCard = header.group(1);
                continue;
            }
            Matcher cardTotal = CARD_TOTAL.matcher(line);
            if (cardTotal.matches()) {
                cardTotals.add(new CardTotal(cardTotal.group(1), cents(cardTotal.group(2))));
                currentCard = null;
                continue;
            }
            Matcher row = LINE.matcher(line);
            if (currentCard != null && row.matches()) {
                lines.add(toLine(currentCard, row));
            }
        }
        if (lines.isEmpty()) {
            throw AppException.statementPdfUnsupported();
        }
        return new ParsedStatement(
                LocalDate.parse(strip.group(1), DUE_DATE), cents(strip.group(2)), expensesCents, lines, cardTotals);
    }

    private static ParsedLine toLine(String card, Matcher row) {
        int month = MONTHS.indexOf(row.group(2).toLowerCase(Locale.ROOT)) + 1;
        if (month == 0) {
            throw AppException.statementPdfUnsupported();
        }
        LocalDate date = LocalDate.of(Integer.parseInt(row.group(3)), month, Integer.parseInt(row.group(1)));
        String description = row.group(4).strip();
        boolean credit = row.group(5) != null;
        Kind kind = !credit ? Kind.EXPENSE : description.contains(PAYMENT_MARKER) ? Kind.PAYMENT : Kind.CREDIT;

        Matcher installment = INSTALLMENT.matcher(description);
        Integer number = null;
        Integer count = null;
        if (installment.find()) {
            number = Integer.parseInt(installment.group(1));
            count = Integer.parseInt(installment.group(2));
        }
        return new ParsedLine(card, date, description, cents(row.group(6)), kind, number, count);
    }

    /** {@code "3.111,96"} → 311196. */
    private static long cents(String brl) {
        return Long.parseLong(brl.replace(".", "").replace(",", ""));
    }
}
