package br.com.mkfinance.api.card;

import br.com.mkfinance.application.card.statementimport.ParsedStatement.Kind;
import br.com.mkfinance.application.card.statementimport.StatementImportPreview;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Contract: {@code StatementImportPreview}. */
public record StatementImportPreviewResponse(
        String statementMonth, LocalDate dueDate, long totalCents, List<LineResponse> lines, List<WarningResponse> warnings) {

    public record LineResponse(
            String cardLast4,
            LocalDate date,
            String description,
            long amountCents,
            Kind kind,
            Integer installmentNumber,
            Integer installmentCount,
            StatementImportPreview.Status status,
            boolean duplicate,
            String externalId,
            List<CandidateResponse> candidates,
            UUID suggestedCategoryId) {
    }

    public record CandidateResponse(UUID installmentId, String description, LocalDate purchaseDate) {
    }

    public record WarningResponse(String code, String detail) {
    }
}
