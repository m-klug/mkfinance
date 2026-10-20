package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.MarketRate;
import br.com.mkfinance.domain.model.MarketRateCode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRateRepository extends JpaRepository<MarketRate, UUID> {

    /** The current value of a rate: the row with the most recent reference date. */
    Optional<MarketRate> findFirstByCodeOrderByReferenceDateDesc(MarketRateCode code);

    Optional<MarketRate> findByCodeAndReferenceDate(MarketRateCode code, LocalDate referenceDate);
}
