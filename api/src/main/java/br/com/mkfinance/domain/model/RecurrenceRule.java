package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
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
 * Maps {@code recurrence_rule} (V14__recurrences.sql) — a recurring series (salary, rent,
 * subscriptions, PLANEJAMENTO §7 Fase 2) that materializes as PENDING {@link Transaction}s, one per
 * occurrence, lazily as each competency is read — see
 * {@code application/recurrence/RecurrenceOccurrenceMaterializer}.
 *
 * <p>The schedule is explicit — {@link #dayOfMonth}, {@link #dayOfWeek}, {@link #monthOfYear} —
 * rather than derived from {@link #startDate} (CT-1): changing which day the rent is due must not
 * move the date the series started on. Which field is required depends on {@link #frequency}; see
 * {@code application/recurrence/RecurrenceRuleService#validateSchedule}.
 *
 * <p>Foreign keys are plain scalar columns, like every other entity here, so lookups always filter
 * by {@code workspaceId} explicitly (R8) and nothing is lazily loaded by accident.
 */
@Entity
@Table(name = "recurrence_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurrenceRule {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /** INCOME or EXPENSE only — a recurring transfer is out of scope of the contract. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TransactionType type;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "note")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private RecurrenceFrequency frequency;

    /** MONTHLY and YEARLY. A day the month does not have falls on the month's last day. */
    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    /** WEEKLY only. */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 9)
    private DayOfWeek dayOfWeek;

    /** YEARLY only, paired with {@link #dayOfMonth}. */
    @Column(name = "month_of_year")
    private Integer monthOfYear;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Inclusive; null means open-ended. Set when a THIS_AND_FUTURE edit/delete closes the rule. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * A closed or user-deactivated rule stops producing new occurrences but keeps the ones already
     * materialized — turning off the subscription does not undo the months you paid.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
