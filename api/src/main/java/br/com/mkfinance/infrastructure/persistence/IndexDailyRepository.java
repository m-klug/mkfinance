package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.IndexCode;
import br.com.mkfinance.domain.model.IndexDaily;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexDailyRepository extends JpaRepository<IndexDaily, UUID> {

    Optional<IndexDaily> findFirstByCodeOrderByReferenceDateAsc(IndexCode code);

    Optional<IndexDaily> findFirstByCodeOrderByReferenceDateDesc(IndexCode code);

    /** Both ends inclusive, oldest first. */
    List<IndexDaily> findByCodeAndReferenceDateBetweenOrderByReferenceDateAsc(IndexCode code, LocalDate from, LocalDate to);
}
