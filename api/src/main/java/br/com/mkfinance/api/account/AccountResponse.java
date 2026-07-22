package br.com.mkfinance.api.account;

import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        long initialBalanceCents,
        /** Always null in E1 — balance calculation belongs to BE-3 (contract note). */
        Long balanceCents,
        Long projectedBalanceCents,
        ContributionPillar defaultContributionPillar,
        String color,
        String icon,
        boolean archived) {
}
