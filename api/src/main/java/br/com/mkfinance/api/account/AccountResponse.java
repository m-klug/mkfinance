package br.com.mkfinance.api.account;

import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        long initialBalanceCents,
        /** Initial balance + settled movements (R5). */
        Long balanceCents,
        /** Initial balance + settled and pending movements — the "saldo projetado" (R5). */
        Long projectedBalanceCents,
        ContributionPillar defaultContributionPillar,
        String color,
        String icon,
        boolean archived,
        /**
         * The account has at least one non-archived vault (BE-9, DESENVOLVIMENTO-CAIXINHAS.md §4.4)
         * — contributions into it must then name one, and {@code defaultContributionPillar} no
         * longer decides. Always sent by the server; {@code !hasVaults} is the correct
         * pre-Caixinhas-compatible check on the client.
         */
        boolean hasVaults) {
}
