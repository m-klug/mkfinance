package br.com.mkfinance.api.account;

import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull AccountType type,
        Long initialBalanceCents,
        ContributionPillar defaultContributionPillar,
        String color,
        String icon) {
}
