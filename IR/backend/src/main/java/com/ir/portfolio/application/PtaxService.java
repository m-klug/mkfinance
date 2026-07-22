package com.ir.portfolio.application;

import com.ir.portfolio.domain.exception.PtaxUnavailableException;
import com.ir.portfolio.domain.model.PtaxQuote;
import com.ir.portfolio.domain.model.PtaxRateType;
import com.ir.portfolio.domain.port.PtaxProvider;
import com.ir.portfolio.infrastructure.bcb.BcbDailyQuote;
import com.ir.portfolio.infrastructure.bcb.BcbPtaxClient;
import com.ir.portfolio.infrastructure.bcb.BcbProperties;
import com.ir.portfolio.infrastructure.persistence.entity.PtaxQuoteEntity;
import com.ir.portfolio.infrastructure.persistence.repository.PtaxQuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read-through PTAX cache backed by the BCB Olinda API.
 *
 * <p>An exact-date cache hit is authoritative (that date's quote never
 * changes). On a miss the whole forward window is fetched from BCB in a single
 * call and persisted, so weekend/holiday requests resolve to the next business
 * day (D+1..D+maxForwardDays) without a local holiday calendar — BCB simply
 * has no rows for non-business days. An empty window raises
 * {@link PtaxUnavailableException} (mirrors the front-end's best-effort D+3 cap).
 */
@Service
public class PtaxService implements PtaxProvider {

    private static final ZoneId BCB_ZONE = ZoneId.of("America/Sao_Paulo");

    private final PtaxQuoteRepository repository;
    private final BcbPtaxClient client;
    private final BcbProperties properties;

    public PtaxService(PtaxQuoteRepository repository, BcbPtaxClient client, BcbProperties properties) {
        this.repository = repository;
        this.client = client;
        this.properties = properties;
    }

    @Override
    @Transactional
    public PtaxQuote get(LocalDate requestedDate, PtaxRateType type) {
        Optional<PtaxQuoteEntity> exact = repository.findById(requestedDate);
        if (exact.isPresent()) {
            return toDomain(exact.get(), type, requestedDate);
        }

        LocalDate windowEnd = requestedDate.plusDays(properties.maxForwardDays());
        List<BcbDailyQuote> fetched = client.quotesBetween(requestedDate, windowEnd);

        for (BcbDailyQuote quote : fetched) {
            LocalDate quoteDate = quote.timestamp().toLocalDate();
            if (!repository.existsById(quoteDate)) {
                ZonedDateTime bcbTimestamp = quote.timestamp().atZone(BCB_ZONE);
                repository.save(new PtaxQuoteEntity(
                        quoteDate, quote.cotacaoCompra(), quote.cotacaoVenda(), bcbTimestamp.toOffsetDateTime()));
            }
        }

        return fetched.stream()
                .filter(q -> !q.timestamp().toLocalDate().isBefore(requestedDate))
                .min(Comparator.comparing(q -> q.timestamp().toLocalDate()))
                .map(q -> new PtaxQuote(
                        rateFor(q.cotacaoCompra(), q.cotacaoVenda(), type),
                        type,
                        q.timestamp().toLocalDate(),
                        requestedDate,
                        !q.timestamp().toLocalDate().equals(requestedDate)))
                .orElseThrow(() -> new PtaxUnavailableException(requestedDate, properties.maxForwardDays()));
    }

    private PtaxQuote toDomain(PtaxQuoteEntity entity, PtaxRateType type, LocalDate requestedDate) {
        return new PtaxQuote(
                rateFor(entity.getBuyRate(), entity.getSellRate(), type),
                type,
                entity.getQuoteDate(),
                requestedDate,
                !entity.getQuoteDate().equals(requestedDate));
    }

    private java.math.BigDecimal rateFor(java.math.BigDecimal buyRate, java.math.BigDecimal sellRate, PtaxRateType type) {
        return type == PtaxRateType.BUY ? buyRate : sellRate;
    }
}
