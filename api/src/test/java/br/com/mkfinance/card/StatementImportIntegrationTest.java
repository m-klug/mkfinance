package br.com.mkfinance.card;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.card.CardPurchaseBatchRequest;
import br.com.mkfinance.api.card.CardPurchaseResponse;
import br.com.mkfinance.api.card.CardResponse;
import br.com.mkfinance.api.card.StatementImportPreviewResponse;
import br.com.mkfinance.api.card.StatementPaymentRequest;
import br.com.mkfinance.api.card.StatementImportPreviewResponse.LineResponse;
import br.com.mkfinance.application.card.statementimport.StatementImportPreview.Status;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.StatementStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Statement PDF import: preview reads the file without writing, the batch endpoint then creates. */
class StatementImportIntegrationTest extends AbstractCardIntegrationTest {

    private static final String PASSWORD = "123456";

    private static final List<String> STATEMENT_LINES = List.of(
            "MURILO KLUG",
            "5364****2148 10/09/2026 R$ 331,00",
            "Despesas do mês R$ 331,00",
            "CARTÃO 5364****2148",
            "10 de ago. 2026 PAGTO DEBITO AUTOMATICO - + R$ 100,00",
            "28 de ago. 2026 KEBAB TIMBO LTDA - R$ 31,00",
            "Total CARTÃO 5364****2148 R$ 31,00",
            "CARTÃO 5364****5576",
            "22 de jul. 2026 LOJA PARCELADA (Parcela 02 de 02) - R$ 100,00",
            "03 de ago. 2026 PADARIA - R$ 200,00",
            "Total CARTÃO 5364****5576 R$ 300,00");

    private CardResponse card;

    @BeforeEach
    void setUp() {
        register();
        // Closing 3, due 10: the bill due on 10/09 is the one that closes in September.
        card = createCard("Inter", 500_000L, 3, 10);
    }

    @Test
    void previewListsLinesSkipsThePaymentAndPicksTheStatementFromTheDueDate() throws IOException {
        StatementImportPreviewResponse preview = preview(pdf(PASSWORD), PASSWORD).getBody();

        assertThat(preview.statementMonth()).isEqualTo("2026-09");
        assertThat(preview.dueDate()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(preview.lines()).extracting(LineResponse::description)
                .containsExactly("KEBAB TIMBO LTDA", "LOJA PARCELADA (Parcela 02 de 02)", "PADARIA");
        assertThat(preview.warnings()).extracting(w -> w.code()).contains("PAYMENT_IGNORED");
        assertThat(statementOfMonth(card.id(), "2026-09").totalCents()).isZero();
    }

    @Test
    void wrongPasswordIsRejectedWithItsOwnCode() throws IOException {
        ResponseEntity<ErrorResponse> response = session.post(
                path("/cards/" + card.id() + "/statement-imports/preview"),
                form(pdf(PASSWORD), "000000"),
                ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.PDF_PASSWORD_INVALID);
    }

    @Test
    void batchChargesEveryLineToTheStatementTheBankDeclared() {
        UUID category = someExpenseCategory().id();
        CardPurchaseBatchRequest request = new CardPurchaseBatchRequest(
                "2026-09",
                List.of(
                        new CardPurchaseBatchRequest.Item(category, "KEBAB TIMBO LTDA", 3_100L, LocalDate.of(2026, 8, 28), null, null),
                        // Dated before the closing day of a previous cycle — would allocate elsewhere
                        // by date alone, but the bill says September.
                        new CardPurchaseBatchRequest.Item(
                                category, "LOJA PARCELADA (Parcela 02 de 02)", 10_000L, LocalDate.of(2026, 7, 22), null, null)));

        ResponseEntity<CardPurchaseResponse[]> response = session.post(
                path("/cards/" + card.id() + "/purchases/batch"), request, CardPurchaseResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allSatisfy(p -> assertThat(p.installments())
                .extracting(i -> i.statementMonth())
                .containsExactly("2026-09"));
        assertThat(statementOfMonth(card.id(), "2026-09").totalCents()).isEqualTo(13_100L);
    }

    @Test
    void aHandTypedPurchaseIsMatchedByAmountAndDateWhateverItsDescription() throws IOException {
        createPurchase(card.id(), "Pastel com os amigos", 20_000L, LocalDate.of(2026, 8, 3), 1);

        LineResponse padaria = line(preview(pdf(PASSWORD), PASSWORD).getBody(), "PADARIA");

        assertThat(padaria.status()).isEqualTo(Status.MATCH);
        assertThat(padaria.candidates()).extracting(c -> c.description()).containsExactly("Pastel com os amigos");
        assertThat(padaria.duplicate()).isFalse();
    }

    @Test
    void aLineOutsideTheDateWindowOrWithAnotherAmountIsNew() throws IOException {
        createPurchase(card.id(), "Longe", 20_000L, LocalDate.of(2026, 7, 20), 1);
        createPurchase(card.id(), "Valor diferente", 20_001L, LocalDate.of(2026, 8, 3), 1);

        assertThat(line(preview(pdf(PASSWORD), PASSWORD).getBody(), "PADARIA").status()).isEqualTo(Status.NEW);
    }

    @Test
    void linkingAttachesTheBillLineToTheHandTypedPurchaseInsteadOfCreatingAnother() throws IOException {
        CardPurchaseResponse manual = createPurchase(card.id(), "Pastel com os amigos", 20_000L, LocalDate.of(2026, 8, 3), 1);
        LineResponse padaria = line(preview(pdf(PASSWORD), PASSWORD).getBody(), "PADARIA");

        ResponseEntity<CardPurchaseResponse[]> response = confirm(link(padaria, padaria.candidates().get(0).installmentId()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEmpty();
        // Still one charge on the bill, not the manual one plus an imported copy.
        assertThat(statementOfMonth(card.id(), manual.installments().get(0).statementMonth()).totalCents())
                .isEqualTo(20_000L);
        assertThat(line(preview(pdf(PASSWORD), PASSWORD).getBody(), "PADARIA").status())
                .isEqualTo(Status.ALREADY_IMPORTED);
    }

    @Test
    void importingTheSameBillTwiceCreatesEachLineOnce() throws IOException {
        StatementImportPreviewResponse first = preview(pdf(PASSWORD), PASSWORD).getBody();
        List<CardPurchaseBatchRequest.Item> items = first.lines().stream()
                .map(l -> new CardPurchaseBatchRequest.Item(
                        someExpenseCategory().id(), l.description(), l.amountCents(), l.date(), l.externalId(), null))
                .toList();
        CardPurchaseBatchRequest request = new CardPurchaseBatchRequest("2026-09", items);

        assertThat(session.post(path("/cards/" + card.id() + "/purchases/batch"), request, CardPurchaseResponse[].class)
                        .getBody())
                .hasSize(3);
        // A stale tab confirming the same preview again must not double the bill.
        assertThat(session.post(path("/cards/" + card.id() + "/purchases/batch"), request, CardPurchaseResponse[].class)
                        .getBody())
                .isEmpty();

        assertThat(statementOfMonth(card.id(), "2026-09").totalCents()).isEqualTo(33_100L);
        assertThat(preview(pdf(PASSWORD), PASSWORD).getBody().lines())
                .allSatisfy(l -> assertThat(l.status()).isEqualTo(Status.ALREADY_IMPORTED));
    }

    @Test
    void twoHandTypedPurchasesThatBothFitMakeTheLineAmbiguous() throws IOException {
        createPurchase(card.id(), "Pastel 1", 20_000L, LocalDate.of(2026, 8, 3), 1);
        createPurchase(card.id(), "Pastel 2", 20_000L, LocalDate.of(2026, 8, 2), 1);

        LineResponse padaria = line(preview(pdf(PASSWORD), PASSWORD).getBody(), "PADARIA");

        assertThat(padaria.status()).isEqualTo(Status.AMBIGUOUS);
        // Closest date first.
        assertThat(padaria.candidates()).extracting(c -> c.description()).containsExactly("Pastel 1", "Pastel 2");
    }

    @Test
    void anInstalmentLineMatchesTheSameInstalmentOfAHandTypedPurchase() throws IOException {
        createPurchase(card.id(), "Geladeira", 20_000L, LocalDate.of(2026, 7, 22), 2);

        LineResponse parcela = line(preview(pdf(PASSWORD), PASSWORD).getBody(), "LOJA PARCELADA (Parcela 02 de 02)");

        assertThat(parcela.status()).isEqualTo(Status.MATCH);
        assertThat(parcela.candidates()).extracting(c -> c.description()).containsExactly("Geladeira");
    }

    @Test
    void anInstallmentAlreadyLinkedCannotBeLinkedAgain() throws IOException {
        createPurchase(card.id(), "Pastel com os amigos", 20_000L, LocalDate.of(2026, 8, 3), 1);
        LineResponse padaria = line(preview(pdf(PASSWORD), PASSWORD).getBody(), "PADARIA");
        UUID installment = padaria.candidates().get(0).installmentId();
        confirm(link(padaria, installment));

        ResponseEntity<ErrorResponse> refused = session.post(
                path("/cards/" + card.id() + "/purchases/batch"),
                new CardPurchaseBatchRequest("2026-09", List.of(new CardPurchaseBatchRequest.Item(
                        someExpenseCategory().id(), "PADARIA", 20_000L, padaria.date(), "fp:outro", installment))),
                ErrorResponse.class);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().code()).isEqualTo(ErrorCode.IMPORT_LINK_INVALID);
    }

    private static LineResponse line(StatementImportPreviewResponse preview, String description) {
        return preview.lines().stream().filter(l -> l.description().equals(description)).findFirst().orElseThrow();
    }

    private static CardPurchaseBatchRequest.Item link(LineResponse line, UUID installmentId) {
        return new CardPurchaseBatchRequest.Item(
                null, line.description(), line.amountCents(), line.date(), line.externalId(), installmentId);
    }

    private ResponseEntity<CardPurchaseResponse[]> confirm(CardPurchaseBatchRequest.Item item) {
        return session.post(
                path("/cards/" + card.id() + "/purchases/batch"),
                new CardPurchaseBatchRequest("2026-09", List.of(item)),
                CardPurchaseResponse[].class);
    }

    @Test
    void batchIsAllOrNothingWhenTheStatementIsAlreadyPaid() {
        UUID category = someExpenseCategory().id();
        var account = createAccount("Conta", 1_000_000L);
        session.post(
                path("/cards/" + card.id() + "/purchases/batch"),
                new CardPurchaseBatchRequest(
                        "2026-09",
                        List.of(new CardPurchaseBatchRequest.Item(category, "X", 1_000L, LocalDate.of(2026, 8, 5), null, null))),
                CardPurchaseResponse[].class);
        var statement = statementOfMonth(card.id(), "2026-09");
        session.post(
                path("/statements/" + statement.id() + "/payments"),
                new StatementPaymentRequest(account.id(), 1_000L, LocalDate.of(2026, 9, 10)),
                String.class);
        assertThat(statementOfMonth(card.id(), "2026-09").status()).isEqualTo(StatementStatus.PAID);

        ResponseEntity<ErrorResponse> refused = session.post(
                path("/cards/" + card.id() + "/purchases/batch"),
                new CardPurchaseBatchRequest(
                        "2026-09",
                        List.of(new CardPurchaseBatchRequest.Item(category, "B", 100L, LocalDate.of(2026, 9, 6), null, null))),
                ErrorResponse.class);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refused.getBody().code()).isEqualTo(ErrorCode.STATEMENT_IMMUTABLE);
        assertThat(statementOfMonth(card.id(), "2026-09").totalCents()).isEqualTo(1_000L);
    }

    private ResponseEntity<StatementImportPreviewResponse> preview(byte[] pdf, String password) {
        return session.post(
                path("/cards/" + card.id() + "/statement-imports/preview"),
                form(pdf, password),
                StatementImportPreviewResponse.class);
    }

    private static MultiValueMap<String, Object> form(byte[] pdf, String password) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "fatura.pdf";
            }
        });
        body.add("password", password);
        return body;
    }

    /** A one-page, password-protected PDF whose text mirrors the Inter layout. */
    private static byte[] pdf(String password) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                content.beginText();
                content.setLeading(14);
                content.newLineAtOffset(30, 780);
                for (String line : STATEMENT_LINES) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
