package br.com.mkfinance.api.account;

import br.com.mkfinance.api.account.BankStatementImportPreviewResponse.LineResponse;
import br.com.mkfinance.api.account.BankStatementImportPreviewResponse.StatementCandidateResponse;
import br.com.mkfinance.api.account.BankStatementImportPreviewResponse.WarningResponse;
import br.com.mkfinance.application.account.statementimport.BankStatementImportItem;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview;
import br.com.mkfinance.application.account.statementimport.BankStatementImportResult;
import br.com.mkfinance.application.account.statementimport.BankStatementImportService;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importing a bank statement (OFX) into an account, in two steps: a preview that writes nothing,
 * then a confirmation that writes everything or nothing. The file is read in memory and not kept.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/accounts/{accountId}/statement-imports")
@RequiredArgsConstructor
public class BankStatementImportController {

    private final BankStatementImportService importService;
    private final WorkspaceContext workspaceContext;

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BankStatementImportPreviewResponse preview(
            @PathVariable UUID workspaceId,
            @PathVariable UUID accountId,
            @RequestPart("file") MultipartFile file)
            throws IOException {
        return toResponse(importService.preview(workspaceContext.getWorkspaceId(), accountId, file.getBytes()));
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public BankStatementImportResponse confirm(
            @PathVariable UUID workspaceId,
            @PathVariable UUID accountId,
            @Valid @RequestBody BankStatementImportRequest request) {
        List<BankStatementImportItem> items = request.items().stream()
                .map(i -> new BankStatementImportItem(
                        i.externalId(),
                        i.mode(),
                        i.type(),
                        i.amountCents(),
                        i.date(),
                        i.description(),
                        i.categoryId(),
                        i.counterpartAccountId(),
                        i.contributionPillar(),
                        i.statementId()))
                .toList();
        BankStatementImportResult result = importService.confirm(workspaceContext.getWorkspaceId(), accountId, items);
        return new BankStatementImportResponse(result.entries(), result.transfers(), result.statementPayments());
    }

    private static BankStatementImportPreviewResponse toResponse(BankStatementImportPreview preview) {
        return new BankStatementImportPreviewResponse(
                preview.bankId(),
                preview.accountLast4(),
                preview.periodStart(),
                preview.periodEnd(),
                preview.accountBalanceCents(),
                preview.ledgerBalanceCents(),
                preview.ledgerBalanceDate(),
                preview.lines().stream()
                        .map(l -> new LineResponse(
                                l.externalId(),
                                l.date(),
                                l.description(),
                                l.type(),
                                l.amountCents(),
                                l.kind(),
                                l.mode(),
                                l.duplicate(),
                                l.suggestedCategoryId(),
                                l.suggestedCounterpartAccountId(),
                                l.suggestedStatementId(),
                                l.statementCandidates().stream()
                                        .map(c -> new StatementCandidateResponse(
                                                c.statementId(),
                                                c.cardId(),
                                                c.cardName(),
                                                c.referenceMonth().toString(),
                                                c.dueDate(),
                                                c.remainingCents()))
                                        .toList(),
                                l.selectedByDefault()))
                        .toList(),
                preview.warnings().stream().map(w -> new WarningResponse(w.code(), w.detail())).toList());
    }
}
