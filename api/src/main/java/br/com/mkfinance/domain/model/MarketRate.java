package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps {@code market_rate} (V24__market_rate.sql). Public data from the Banco Central, shared by
 * every workspace, hence no {@code workspaceId}. One row per rate and reference date; the current
 * value of a rate is its row with the latest {@link #referenceDate}.
 */
@Entity
@Table(name = "market_rate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketRate {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private MarketRateCode code;

    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Column(name = "rate_percent", nullable = false, precision = 9, scale = 4)
    private BigDecimal ratePercent;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;
}
