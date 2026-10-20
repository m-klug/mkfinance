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
 * Maps {@code index_daily} (V25__index_daily.sql): one business day of the CDI or the Selic, in
 * percent per day. Public data from the Banco Central, shared by every workspace.
 */
@Entity
@Table(name = "index_daily")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexDaily {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private IndexCode code;

    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Column(name = "daily_percent", nullable = false, precision = 14, scale = 8)
    private BigDecimal dailyPercent;

    @Column(name = "fetched_at", nullable = false)
    private OffsetDateTime fetchedAt;
}
