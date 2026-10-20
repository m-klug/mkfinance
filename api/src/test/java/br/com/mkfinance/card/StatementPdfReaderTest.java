package br.com.mkfinance.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.mkfinance.application.card.statementimport.InterStatementParser;
import br.com.mkfinance.application.card.statementimport.ParsedStatement;
import br.com.mkfinance.application.card.statementimport.StatementPdfReader;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real PDF → text → lines path against a statement kept out of git (it holds personal
 * data). Skipped, not failed, when the sample or its password is absent: set
 * {@code MKFINANCE_SAMPLE_STATEMENT_PASSWORD} and put the file in {@code docs/samples/Fatura.pdf}.
 */
class StatementPdfReaderTest {

    private static final Path SAMPLE = Path.of("../docs/samples/Fatura.pdf");

    private final StatementPdfReader reader = new StatementPdfReader();

    @Test
    void parsesTheRealStatementAndItsTotalsAgree() throws IOException {
        String password = System.getenv("MKFINANCE_SAMPLE_STATEMENT_PASSWORD");
        assumeTrue(Files.exists(SAMPLE) && password != null, "sample statement or password not available");

        String text = reader.readText(Files.readAllBytes(SAMPLE), password);
        ParsedStatement statement = new InterStatementParser().parse(text);

        long expenses = statement.lines().stream()
                .filter(l -> l.kind() == ParsedStatement.Kind.EXPENSE)
                .mapToLong(ParsedStatement.ParsedLine::amountCents)
                .sum();
        assertThat(expenses).isEqualTo(statement.expensesCents());
        statement.cardTotals().forEach(total -> assertThat(statement.lines().stream()
                        .filter(l -> l.kind() == ParsedStatement.Kind.EXPENSE && l.cardLast4().equals(total.cardLast4()))
                        .mapToLong(ParsedStatement.ParsedLine::amountCents)
                        .sum())
                .isEqualTo(total.totalCents()));
    }

    @Test
    void wrongPasswordIsReportedAsSuch() throws IOException {
        assumeTrue(Files.exists(SAMPLE), "sample statement not available");

        assertThatThrownBy(() -> reader.readText(Files.readAllBytes(SAMPLE), "000000"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getCode())
                .isEqualTo(ErrorCode.PDF_PASSWORD_INVALID);
    }

    @Test
    void garbageIsReportedAsUnreadable() {
        assertThatThrownBy(() -> reader.readText("not a pdf".getBytes(), ""))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getCode())
                .isEqualTo(ErrorCode.PDF_UNREADABLE);
    }
}
