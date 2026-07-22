package com.ir.portfolio.infrastructure.persistence.repository;

import com.ir.portfolio.infrastructure.persistence.entity.PtaxQuoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PtaxQuoteRepository extends JpaRepository<PtaxQuoteEntity, LocalDate> {

    List<PtaxQuoteEntity> findByQuoteDateBetweenOrderByQuoteDateAsc(LocalDate start, LocalDate end);
}
