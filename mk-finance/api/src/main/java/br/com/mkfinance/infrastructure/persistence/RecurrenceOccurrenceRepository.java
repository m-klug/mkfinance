package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.RecurrenceOccurrence;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurrenceOccurrenceRepository extends JpaRepository<RecurrenceOccurrence, UUID> {

    Optional<RecurrenceOccurrence> findByRuleIdAndOccurrenceDate(UUID ruleId, LocalDate occurrenceDate);

    List<RecurrenceOccurrence> findByRuleIdAndOccurrenceDateIn(UUID ruleId, Collection<LocalDate> occurrenceDates);

    /** Still-scheduled or already-materialized occurrences at or after a cutover — used to close a rule (CT-1). */
    List<RecurrenceOccurrence> findByRuleIdAndOccurrenceDateGreaterThanEqual(UUID ruleId, LocalDate from);
}
