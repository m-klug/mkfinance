package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.CategoryTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryTemplateRepository extends JpaRepository<CategoryTemplate, Short> {

    List<CategoryTemplate> findAllByOrderBySortOrderAsc();
}
