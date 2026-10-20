package br.com.mkfinance.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.application.card.statementimport.InterStatementParser;
import br.com.mkfinance.application.card.statementimport.ParsedStatement;
import br.com.mkfinance.application.card.statementimport.ParsedStatement.Kind;
import br.com.mkfinance.application.card.statementimport.ParsedStatement.ParsedLine;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.exception.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Pure text-in, lines-out: the PDF layer is covered separately by {@link StatementPdfReaderTest}. */
class InterStatementParserTest {

    private static final String TEXT = """
            Resumo da fatura
            Total da sua fatura R$ 5.000,00
            R$ 3.111,96 Data de Vencimento
            10/09/2026
            DESPESAS DO MÊS
            Despesas do mês R$ 3.111,96
            MURILO KLUG
            5364****2148 10/09/2026 R$ 3.111,96
            Despesas da fatura
            CARTÃO 5364****2148
            Data Movimentação Beneficiário Valor
            10 de ago. 2026 PAGTO DEBITO AUTOMATICO - + R$ 2.499,96
            28 de ago. 2026 KEBAB TIMBO LTDA - R$ 31,00
            Total CARTÃO 5364****2148 R$ 31,00
            CARTÃO 5364****5576
            Data Movimentação Beneficiário Valor
            22 de jul. 2026 MERCADOLIVRE*MERCADOL (Parcela 02 de 02) - R$ 239,99
            10 de ago. 2026 GIFT CARD - R$ 210,00
            11 de ago. 2026 ESTORNO LOJA X - + R$ 17,98
            Total CARTÃO 5364****5576 R$ 2.000,00
            """;

    private final InterStatementParser parser = new InterStatementParser();

    @Test
    void readsHeaderTotalsAndEveryTableRow() {
        ParsedStatement statement = parser.parse(TEXT);

        assertThat(statement.dueDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(statement.totalCents()).isEqualTo(311196);
        assertThat(statement.expensesCents()).isEqualTo(311196);
        assertThat(statement.lines()).hasSize(5);
        assertThat(statement.cardTotals()).extracting(t -> t.cardLast4() + ":" + t.totalCents())
                .containsExactly("2148:3100", "5576:200000");
    }

    @Test
    void classifiesPaymentsCreditsAndExpenses() {
        ParsedStatement statement = parser.parse(TEXT);

        assertThat(statement.lines()).extracting(ParsedLine::kind)
                .containsExactly(Kind.PAYMENT, Kind.EXPENSE, Kind.EXPENSE, Kind.EXPENSE, Kind.CREDIT);
    }

    @Test
    void readsDateDescriptionAndCardOfARow() {
        ParsedLine line = parser.parse(TEXT).lines().get(1);

        assertThat(line.cardLast4()).isEqualTo("2148");
        assertThat(line.date()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(line.description()).isEqualTo("KEBAB TIMBO LTDA");
        assertThat(line.amountCents()).isEqualTo(3100);
        assertThat(line.installmentNumber()).isNull();
    }

    @Test
    void readsInstallmentMarkerAndKeepsItInTheDescription() {
        ParsedLine line = parser.parse(TEXT).lines().get(2);

        assertThat(line.installmentNumber()).isEqualTo(2);
        assertThat(line.installmentCount()).isEqualTo(2);
        assertThat(line.description()).isEqualTo("MERCADOLIVRE*MERCADOL (Parcela 02 de 02)");
        assertThat(line.amountCents()).isEqualTo(23999);
    }

    @Test
    void rejectsTextThatIsNotAnInterStatement() {
        assertThatThrownBy(() -> parser.parse("Extrato qualquer\nsem fatura"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getCode())
                .isEqualTo(ErrorCode.STATEMENT_PDF_UNSUPPORTED);
    }
}
