package br.com.mkfinance.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.application.account.statementimport.OfxParser;
import br.com.mkfinance.application.account.statementimport.ParsedBankStatement;
import br.com.mkfinance.application.account.statementimport.ParsedBankStatement.ParsedBankTransaction;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.exception.ErrorCode;
import java.nio.charset.Charset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Synthetic OFX only: the real sample carries third parties' names and never enters a test. */
class OfxParserTest {

    private static final String HEADER = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            SECURITY:NONE
            ENCODING:USASCII
            CHARSET:1252
            COMPRESSION:NONE
            OLDFILEUID:NONE
            NEWFILEUID:NONE

            """;

    private static String statement(String transactions, String extra) {
        return HEADER + """
                <OFX>
                <BANKMSGSRSV1>
                <STMTTRNRS>
                <STMTRS>
                <CURDEF>BRL
                <BANKACCTFROM>
                <BANKID>077
                <ACCTID>123456789
                <ACCTTYPE>CHECKING
                </BANKACCTFROM>
                <BANKTRANLIST>
                <DTSTART>20260929
                <DTEND>20261019
                """ + transactions + """
                </BANKTRANLIST>
                """ + extra + """
                </STMTRS>
                </STMTTRNRS>
                </BANKMSGSRSV1>
                </OFX>
                """;
    }

    private static final String LEDGER = """
            <LEDGERBAL>
            <BALAMT>9363.50
            <DTASOF>20261019
            </LEDGERBAL>
            <AVAILBAL>
            <BALAMT>1.00
            <DTASOF>20260101
            </AVAILBAL>
            """;

    private final OfxParser parser = new OfxParser();

    private static byte[] bytes(String text) {
        return text.replace("\n", "\r\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void readsUnclosedLeavesAccountPeriodAndLedgerBalance() {
        String transactions = """
                <STMTTRN>
                <TRNTYPE>CREDIT
                <DTPOSTED>20261019
                <TRNAMT>79.00
                <FITID>202610190771
                <MEMO>Pix recebido
                <NAME>Fulano De Tal
                </STMTTRN>
                <STMTTRN>
                <TRNTYPE>PAYMENT
                <DTPOSTED>20261015120000[-3:BRT]
                <TRNAMT>-1193.76
                <FITID>202610150772
                <MEMO>Pagamento efetuado
                </STMTTRN>
                """;

        ParsedBankStatement parsed = parser.parse(bytes(statement(transactions, LEDGER)));

        assertThat(parsed.bankId()).isEqualTo("077");
        assertThat(parsed.accountId()).isEqualTo("123456789");
        assertThat(parsed.periodStart()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(parsed.periodEnd()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(parsed.ledgerBalanceCents()).isEqualTo(936350L);
        assertThat(parsed.ledgerBalanceDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(parsed.transactions()).containsExactly(
                new ParsedBankTransaction("202610190771", LocalDate.of(2026, 10, 19), 7900L, "Pix recebido", "Fulano De Tal"),
                new ParsedBankTransaction("202610150772", LocalDate.of(2026, 10, 15), -119376L, "Pagamento efetuado", null));
    }

    @Test
    void readsClosedLeavesToo() {
        String closed = """
                <STMTTRN>
                <TRNTYPE>CREDIT</TRNTYPE>
                <DTPOSTED>20261017</DTPOSTED>
                <TRNAMT>150.00</TRNAMT>
                <FITID>X1</FITID>
                <MEMO>Entrada</MEMO>
                </STMTTRN>
                """;

        ParsedBankStatement parsed = parser.parse(bytes(statement(closed, "")));

        assertThat(parsed.transactions()).hasSize(1);
        assertThat(parsed.transactions().get(0).amountCents()).isEqualTo(15000L);
        assertThat(parsed.ledgerBalanceCents()).isNull();
    }

    @Test
    void decodesAccentsDeclaredAsWindows1252() {
        String transactions = """
                <STMTTRN>
                <DTPOSTED>20261017
                <TRNAMT>-10.00
                <FITID>X2
                <MEMO>Pagamento efetuado: "Condomínio - Outubro" &amp; taxa
                </STMTTRN>
                """;
        byte[] content = statement(transactions, "").getBytes(Charset.forName("windows-1252"));

        ParsedBankStatement parsed = parser.parse(content);

        assertThat(parsed.transactions().get(0).memo()).isEqualTo("Pagamento efetuado: \"Condomínio - Outubro\" & taxa");
    }

    @Test
    void decodesUtf8WhenNoLegacyCharsetIsDeclared() {
        String body = statement("""
                <STMTTRN>
                <DTPOSTED>20261017
                <TRNAMT>-10.00
                <FITID>X3
                <MEMO>Açaí
                </STMTTRN>
                """, "").replace("ENCODING:USASCII", "ENCODING:UTF-8").replace("CHARSET:1252", "CHARSET:NONE");

        ParsedBankStatement parsed = parser.parse(body.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed.transactions().get(0).memo()).isEqualTo("Açaí");
    }

    @Test
    void keepsCentsExactAndAcceptsDecimalComma() {
        String transactions = """
                <STMTTRN>
                <DTPOSTED>20261017
                <TRNAMT>-0.10
                <FITID>A
                </STMTTRN>
                <STMTTRN>
                <DTPOSTED>20261017
                <TRNAMT>1234,56
                <FITID>B
                </STMTTRN>
                """;

        ParsedBankStatement parsed = parser.parse(bytes(statement(transactions, "")));

        assertThat(parsed.transactions()).extracting(ParsedBankTransaction::amountCents).containsExactly(-10L, 123456L);
        assertThat(parsed.transactions().get(0).memo()).isEmpty();
    }

    @Test
    void refusesWhatIsNotAnOfxStatementWithTransactions() {
        assertThatThrownBy(() -> parser.parse("not an ofx".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OFX_UNREADABLE));
        assertThatThrownBy(() -> parser.parse(new byte[0]))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OFX_UNREADABLE));
        assertThatThrownBy(() -> parser.parse(bytes(statement("", ""))))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OFX_UNREADABLE));
    }

    @Test
    void refusesTransactionWithoutItsBankIdAndNonBrlStatements() {
        String noId = """
                <STMTTRN>
                <DTPOSTED>20261017
                <TRNAMT>1.00
                </STMTTRN>
                """;
        assertThatThrownBy(() -> parser.parse(bytes(statement(noId, ""))))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OFX_UNREADABLE));

        String usd = statement("""
                <STMTTRN>
                <DTPOSTED>20261017
                <TRNAMT>1.00
                <FITID>U
                </STMTTRN>
                """, "").replace("<CURDEF>BRL", "<CURDEF>USD");
        assertThatThrownBy(() -> parser.parse(bytes(usd)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OFX_UNSUPPORTED));
    }

    @Test
    void refusesFilesWithMoreThanOneAccount() {
        String one = statement("""
                <STMTTRN>
                <DTPOSTED>20261017
                <TRNAMT>1.00
                <FITID>M
                </STMTTRN>
                """, "");
        String two = one.replace("</OFX>", one.substring(one.indexOf("<STMTTRNRS>"), one.indexOf("</STMTTRNRS>") + 12) + "</OFX>");

        assertThatThrownBy(() -> parser.parse(bytes(two)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OFX_UNSUPPORTED));
    }

    /** The real export from the bank, ignored by git: runs only on a machine that has it. */
    @Test
    void readsTheRealSampleWhenPresent() throws IOException {
        Path sample = Path.of("../docs/samples/Extrato-29-09-2026-a-19-10-2026-OFX.ofx");
        Assumptions.assumeTrue(Files.exists(sample));

        ParsedBankStatement parsed = parser.parse(Files.readAllBytes(sample));

        assertThat(parsed.bankId()).isEqualTo("077");
        assertThat(parsed.transactions()).hasSize(30);
        assertThat(parsed.ledgerBalanceCents()).isEqualTo(936350L);
        assertThat(parsed.transactions()).extracting(ParsedBankTransaction::externalId).doesNotHaveDuplicates();
    }
}
