package br.com.mkfinance.application.account;

import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.Pillar;

/** Service-layer command, decoupled from the api-layer {@code AccountRequest} DTO. */
public record AccountInput(
        String name,
        AccountType type,
        long initialBalanceCents,
        Pillar defaultContributionPillar,
        String color,
        String icon) {
}
