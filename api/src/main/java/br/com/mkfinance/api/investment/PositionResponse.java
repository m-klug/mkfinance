package br.com.mkfinance.api.investment;

import br.com.mkfinance.domain.model.PositionStatus;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contract schema {@code Position}. Money in cents.
 *
 * @param origin {@code CONTRIBUTION} when born from a purchase transfer, {@code MANUAL} for a title
 *     bought before the app
 * @param contributionTransactionId the purchase transfer leg; absent for a manual position
 * @param awaitingPayment active and past its maturity: the title is due and the credit has not been
 *     booked yet
 * @param settlement present once the title was paid
 * @param estimate absent for a settled position, and when it cannot be computed
 */
public record PositionResponse(
        UUID id,
        String origin,
        UUID contributionTransactionId,
        UUID accountId,
        String accountName,
        UUID vaultId,
        String vaultName,
        String name,
        InstrumentType type,
        Indexer indexer,
        BigDecimal rate,
        long principalCents,
        LocalDate purchaseDate,
        LocalDate maturityDate,
        PositionStatus status,
        boolean awaitingPayment,
        Settlement settlement,
        Estimate estimate) {

    public record Settlement(
            LocalDate settledDate,
            long receivedCents,
            Long estimatedNetCents,
            Long yieldCents,
            UUID returnTransferTransactionId) {
    }

    /**
     * What the title is worth on {@code asOf}, if redeemed that day.
     *
     * @param lastIndexDate the latest daily index used for the days already lived; earlier than
     *     expected when the Banco Central is behind. Absent for a fixed rate
     */
    public record Valuation(
            LocalDate asOf,
            long grossCents,
            long grossYieldCents,
            long iofCents,
            long incomeTaxCents,
            long custodyCents,
            long netCents,
            long netYieldCents,
            LocalDate lastIndexDate) {
    }

    /** @param today the value now (capped at the maturity for a title already due) */
    public record Estimate(Valuation today, Valuation atMaturity) {
    }
}
