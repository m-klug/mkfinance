package br.com.mkfinance.card;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.account.BankStatementImportPreviewResponse;
import br.com.mkfinance.api.account.BankStatementImportPreviewResponse.LineResponse;
import br.com.mkfinance.api.account.BankStatementImportRequest;
import br.com.mkfinance.api.account.BankStatementImportResponse;
import br.com.mkfinance.api.card.CardPurchaseResponse;
import br.com.mkfinance.api.card.CardResponse;
import br.com.mkfinance.api.card.StatementPaymentRequest;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Duplicate;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Kind;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Mode;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.StatementStatus;
import br.com.mkfinance.domain.model.TransactionType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Bank statement (OFX) import: the preview reads and classifies without writing; the confirmation
 * records each line through the rule that owns it and refuses to record the same bank id twice.
 */
class BankStatementImportIntegrationTest extends AbstractCardIntegrationTest {

    private static final String OFX = """
            OFXHEADER:100
            DATA:OFXSGML
            VERSION:102
            ENCODING:USASCII
            CHARSET:1252

            <OFX>
            <BANKMSGSRSV1><STMTTRNRS><STMTRS>
            <CURDEF>BRL
            <BANKACCTFROM><BANKID>077<ACCTID>987654321<ACCTTYPE>CHECKING</BANKACCTFROM>
            <BANKTRANLIST>
            <DTSTART>20260901<DTEND>20260910
            <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260908<TRNAMT>500.00<FITID>202609080771
            <MEMO>Pix recebido: "Cp :20018183-FULANO"<NAME>Fulano De Tal</STMTTRN>
            <STMTTRN><TRNTYPE>PAYMENT<DTPOSTED>20260908<TRNAMT>-200.00<FITID>202609080772
            <MEMO>Aplicacao: "CDB Porq Obj BANCO INTER S A"</STMTTRN>
            <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20260909<TRNAMT>374.88<FITID>202609090771
            <MEMO>Vencimento Renda Fixa: "CDB POS DI LIQ. BANCO INTER S A"</STMTTRN>
            <STMTTRN><TRNTYPE>PAYMENT<DTPOSTED>20260910<TRNAMT>-100.00<FITID>202609100771
            <MEMO>Pagamento efetuado: "Debito Automatico Fatura Cartao Inter"</STMTTRN>
            </BANKTRANLIST>
            <LEDGERBAL><BALAMT>1234.56<DTASOF>20260910</LEDGERBAL>
            </STMTRS></STMTTRNRS></BANKMSGSRSV1>
            </OFX>
            """;

    private AccountResponse checking;
    private AccountResponse invest;
    private CardResponse card;

    @BeforeEach
    void setUp() {
        register();
        checking = createAccount("Inter", 0L);
        invest = session.post(
                        path("/accounts"),
                        new AccountRequest("Invest", AccountType.INVESTMENT, 0L, ContributionPillar.FINANCIAL_FREEDOM, null, null),
                        AccountResponse.class)
                .getBody();
        // Closing 3, due 10: a purchase of 5 Aug lands on the bill due 10/09.
        card = createCard("Inter", 500_000L, 3, 10);
    }

    @Test
    void previewClassifiesEachLineAndMatchesTheCardBillByAmount() {
        openStatementOf(10_000L);

        BankStatementImportPreviewResponse preview = preview(OFX).getBody();

        assertThat(preview.bankId()).isEqualTo("077");
        assertThat(preview.accountLast4()).isEqualTo("4321");
        assertThat(preview.ledgerBalanceCents()).isEqualTo(123_456L);
        assertThat(preview.lines()).hasSize(4);

        LineResponse pix = line(preview, "202609080771");
        assertThat(pix.description()).isEqualTo("Pix recebido - Fulano De Tal");
        assertThat(pix.type()).isEqualTo(TransactionType.INCOME);
        assertThat(pix.amountCents()).isEqualTo(50_000L);
        assertThat(pix.mode()).isEqualTo(Mode.ENTRY);
        assertThat(pix.selectedByDefault()).isTrue();

        LineResponse application = line(preview, "202609080772");
        assertThat(application.kind()).isEqualTo(Kind.INVESTMENT_OUT);
        assertThat(application.mode()).isEqualTo(Mode.TRANSFER);
        assertThat(application.suggestedCounterpartAccountId()).isEqualTo(invest.id());
        assertThat(application.selectedByDefault()).isTrue();

        LineResponse maturity = line(preview, "202609090771");
        assertThat(maturity.kind()).isEqualTo(Kind.INVESTMENT_IN);
        assertThat(maturity.type()).isEqualTo(TransactionType.INCOME);

        LineResponse bill = line(preview, "202609100771");
        assertThat(bill.kind()).isEqualTo(Kind.CARD_BILL_PAYMENT);
        assertThat(bill.mode()).isEqualTo(Mode.STATEMENT_PAYMENT);
        assertThat(bill.suggestedStatementId()).isNotNull();
        assertThat(bill.selectedByDefault()).isTrue();
        assertThat(preview.warnings()).isEmpty();
        assertThat(balanceOf(checking)).isZero();
    }

    @Test
    void cardBillWithoutAnExactStatementFallsBackToUnselectedWithAWarning() {
        openStatementOf(99_999L);

        BankStatementImportPreviewResponse preview = preview(OFX).getBody();

        LineResponse bill = line(preview, "202609100771");
        assertThat(bill.suggestedStatementId()).isNull();
        assertThat(bill.selectedByDefault()).isFalse();
        assertThat(bill.statementCandidates()).hasSize(1);
        assertThat(preview.warnings()).extracting(w -> w.code()).containsExactly("STATEMENT_PAYMENT_UNMATCHED");
    }

    @Test
    void investmentLinesAreNotPreselectedWhenThereIsNoSingleInvestmentAccount() {
        session.post(
                path("/accounts"),
                new AccountRequest("Outra invest", AccountType.INVESTMENT, 0L, ContributionPillar.GOALS, null, null),
                AccountResponse.class);

        LineResponse application = line(preview(OFX).getBody(), "202609080772");

        assertThat(application.suggestedCounterpartAccountId()).isNull();
        assertThat(application.selectedByDefault()).isFalse();
    }

    @Test
    void confirmRecordsEntriesTransfersAndBillPaymentsAndNeverTwice() {
        UUID category = someCategory(br.com.mkfinance.domain.model.CategoryType.INCOME).id();
        UUID statementId = openStatementOf(10_000L);
        BankStatementImportPreviewResponse preview = preview(OFX).getBody();

        ResponseEntity<BankStatementImportResponse> created = confirm(List.of(
                item("202609080771", Mode.ENTRY, TransactionType.INCOME, 50_000L, "2026-09-08", "Pix recebido - Fulano", category, null, null),
                item("202609080772", Mode.TRANSFER, TransactionType.EXPENSE, 20_000L, "2026-09-08", "Aplicacao: CDB", null, invest.id(), null),
                item("202609090771", Mode.TRANSFER, TransactionType.INCOME, 37_488L, "2026-09-09", "Vencimento Renda Fixa: CDB", null, invest.id(), null),
                item("202609100771", Mode.STATEMENT_PAYMENT, TransactionType.EXPENSE, 10_000L, "2026-09-10", "Fatura", null, null, statementId)));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isEqualTo(new BankStatementImportResponse(1, 2, 1));
        // +500,00 entry, -200,00 out to Invest, +374,88 back, -100,00 card bill.
        assertThat(balanceOf(checking)).isEqualTo(50_000L - 20_000L + 37_488L - 10_000L);
        assertThat(balanceOf(invest)).isEqualTo(20_000L - 37_488L);
        assertThat(statementStatus(statementId)).isEqualTo(StatementStatus.PAID);

        // The same file again: every line is now known by its bank id, and confirming is refused.
        assertThat(preview(OFX).getBody().lines()).allSatisfy(l -> {
            assertThat(l.duplicate()).isEqualTo(Duplicate.EXACT);
            assertThat(l.selectedByDefault()).isFalse();
        });
        ResponseEntity<ErrorResponse> again = session.post(
                path("/accounts/" + checking.id() + "/statement-imports/confirm"),
                new BankStatementImportRequest(List.of(
                        item("202609080771", Mode.ENTRY, TransactionType.INCOME, 50_000L, "2026-09-08", "Pix", category, null, null))),
                ErrorResponse.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().code()).isEqualTo(ErrorCode.OFX_ITEM_ALREADY_IMPORTED);
        assertThat(balanceOf(checking)).isEqualTo(50_000L - 20_000L + 37_488L - 10_000L);
        assertThat(preview).isNotNull();
    }

    @Test
    void oneRefusedLineRecordsNothing() {
        UUID category = someCategory(br.com.mkfinance.domain.model.CategoryType.INCOME).id();

        ResponseEntity<ErrorResponse> refused = session.post(
                path("/accounts/" + checking.id() + "/statement-imports/confirm"),
                new BankStatementImportRequest(List.of(
                        item("A1", Mode.ENTRY, TransactionType.INCOME, 50_000L, "2026-09-08", "Ok", category, null, null),
                        // No counterpart account: refused after the first line was already created.
                        item("A2", Mode.TRANSFER, TransactionType.EXPENSE, 20_000L, "2026-09-08", "Sem destino", null, null, null))),
                ErrorResponse.class);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody().code()).isEqualTo(ErrorCode.OFX_ITEM_INCOMPLETE);
        assertThat(balanceOf(checking)).isZero();
    }

    @Test
    void aMovementTypedByHandIsFlaggedAsAPossibleDuplicate() {
        UUID category = someCategory(br.com.mkfinance.domain.model.CategoryType.INCOME).id();
        // Typed by hand: no bank id, same day, amount and direction as the Pix in the file.
        session.post(
                path("/transactions"),
                new br.com.mkfinance.api.transaction.TransactionRequest(
                        checking.id(), category, TransactionType.INCOME, 50_000L, LocalDate.of(2026, 9, 8), "Salario", null, null, null),
                String.class);

        LineResponse pix = line(preview(OFX).getBody(), "202609080771");

        assertThat(pix.duplicate()).isEqualTo(Duplicate.POSSIBLE);
        assertThat(pix.selectedByDefault()).isFalse();
    }

    @Test
    void notAnOfxFileIsRefusedWithItsOwnCode() {
        ResponseEntity<ErrorResponse> response = session.post(
                path("/accounts/" + checking.id() + "/statement-imports/preview"),
                form("isto nao e um extrato"),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.OFX_UNREADABLE);
    }

    /** A statement due 10/09 owing {@code cents}; returns its id. */
    private UUID openStatementOf(long cents) {
        CardPurchaseResponse purchase = createPurchase(card.id(), "Compra", cents, LocalDate.of(2026, 8, 5), 1);
        return statementOfMonth(card.id(), purchase.installments().get(0).statementMonth()).id();
    }

    private StatementStatus statementStatus(UUID statementId) {
        return statementOfMonth(card.id(), "2026-09").status();
    }

    private long balanceOf(AccountResponse account) {
        AccountResponse[] accounts = session.get(path("/accounts"), AccountResponse[].class).getBody();
        return java.util.Arrays.stream(accounts)
                .filter(a -> a.id().equals(account.id()))
                .findFirst()
                .orElseThrow()
                .balanceCents();
    }

    private static LineResponse line(BankStatementImportPreviewResponse preview, String externalId) {
        return preview.lines().stream().filter(l -> l.externalId().equals(externalId)).findFirst().orElseThrow();
    }

    private static BankStatementImportRequest.Item item(
            String externalId,
            Mode mode,
            TransactionType type,
            long amountCents,
            String date,
            String description,
            UUID categoryId,
            UUID counterpartAccountId,
            UUID statementId) {
        return new BankStatementImportRequest.Item(
                externalId, mode, type, amountCents, LocalDate.parse(date), description, categoryId, counterpartAccountId, null, statementId);
    }

    private ResponseEntity<BankStatementImportResponse> confirm(List<BankStatementImportRequest.Item> items) {
        return session.post(
                path("/accounts/" + checking.id() + "/statement-imports/confirm"),
                new BankStatementImportRequest(items),
                BankStatementImportResponse.class);
    }

    private ResponseEntity<BankStatementImportPreviewResponse> preview(String ofx) {
        return session.post(
                path("/accounts/" + checking.id() + "/statement-imports/preview"),
                form(ofx),
                BankStatementImportPreviewResponse.class);
    }

    private static MultiValueMap<String, Object> form(String ofx) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(ofx.replace("\n", "\r\n").getBytes(StandardCharsets.ISO_8859_1)) {
            @Override
            public String getFilename() {
                return "extrato.ofx";
            }
        });
        return body;
    }
}
