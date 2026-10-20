package br.com.mkfinance.api.account;

import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Duplicate;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Kind;
import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Mode;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contract: {@code BankStatementImportPreview}. */
public record BankStatementImportPreviewResponse(
        String bankId,
        String accountLast4,
        LocalDate periodStart,
        LocalDate periodEnd,
        long accountBalanceCents,
        Long ledgerBalanceCents,
        LocalDate ledgerBalanceDate,
        List<LineResponse> lines,
        List<WarningResponse> warnings) {

    public record LineResponse(
            String externalId,
            LocalDate date,
            String description,
            TransactionType type,
            long amountCents,
            Kind kind,
            Mode mode,
            Duplicate duplicate,
            UUID suggestedCategoryId,
            UUID suggestedCounterpartAccountId,
            UUID suggestedStatementId,
            List<StatementCandidateResponse> statementCandidates,
            boolean selectedByDefault) {
    }

    public record StatementCandidateResponse(
            UUID statementId, UUID cardId, String cardName, String referenceMonth, LocalDate dueDate, long remainingCents) {
    }

    public record WarningResponse(String code, String detail) {
    }
}
