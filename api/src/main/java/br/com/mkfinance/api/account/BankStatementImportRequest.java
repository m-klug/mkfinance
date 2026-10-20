package br.com.mkfinance.api.account;

import br.com.mkfinance.application.account.statementimport.BankStatementImportPreview.Mode;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Contract: {@code BankStatementImportRequest}. Which optional field a line needs depends on its
 * {@code mode}; the service refuses an incomplete line with OFX_ITEM_INCOMPLETE.
 */
public record BankStatementImportRequest(@NotEmpty @Size(max = 500) List<@Valid @NotNull Item> items) {

    public record Item(
            @NotBlank @Size(max = 64) String externalId,
            @NotNull Mode mode,
            @NotNull TransactionType type,
            @NotNull @Positive Long amountCents,
            @NotNull LocalDate date,
            @NotBlank @Size(max = 255) String description,
            UUID categoryId,
            UUID counterpartAccountId,
            ContributionPillar contributionPillar,
            UUID statementId) {
    }
}
