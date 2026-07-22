package br.com.mkfinance.application.workspace;

import br.com.mkfinance.domain.model.Category;
import br.com.mkfinance.domain.model.CategoryTemplate;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.domain.model.PillarAllocation;
import br.com.mkfinance.domain.model.PillarAllocationId;
import br.com.mkfinance.domain.model.Workspace;
import br.com.mkfinance.infrastructure.persistence.CategoryRepository;
import br.com.mkfinance.infrastructure.persistence.CategoryTemplateRepository;
import br.com.mkfinance.infrastructure.persistence.PillarAllocationRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * BE-2: seeds a freshly created workspace with its defaults, invoked by
 * {@link WorkspaceService#createWorkspace} (BE-1's register flow goes through that same path).
 *
 * <ul>
 *   <li>Copies {@code category_template} (V2__seed.sql, pt-BR) into the workspace's own
 *       {@code category} rows — templates are data, not i18n resources (PLANEJAMENTO §3.3):
 *       once copied they're plain user-editable categories.</li>
 *   <li>Inserts the fixed MVP {@code pillar_allocation} targets, summing to 100:
 *       FIXED_COSTS 30, COMFORT 10, GOALS 20, PLEASURES 10, FINANCIAL_FREEDOM 25, KNOWLEDGE 5.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WorkspaceDefaultsSeeder {

    private static final Map<Pillar, Short> FIXED_TARGET_PERCENTS = Map.of(
            Pillar.FIXED_COSTS, (short) 30,
            Pillar.COMFORT, (short) 10,
            Pillar.GOALS, (short) 20,
            Pillar.PLEASURES, (short) 10,
            Pillar.FINANCIAL_FREEDOM, (short) 25,
            Pillar.KNOWLEDGE, (short) 5);

    private final CategoryTemplateRepository categoryTemplateRepository;
    private final CategoryRepository categoryRepository;
    private final PillarAllocationRepository pillarAllocationRepository;

    public void seed(Workspace workspace) {
        seedCategories(workspace);
        seedPillarAllocations(workspace);
    }

    private void seedCategories(Workspace workspace) {
        List<CategoryTemplate> templates = categoryTemplateRepository.findAllByOrderBySortOrderAsc();
        List<Category> categories = templates.stream()
                .map(template -> Category.builder()
                        .workspaceId(workspace.getId())
                        .name(template.getName())
                        .type(template.getType())
                        .pillar(template.getPillar())
                        .icon(template.getIcon())
                        .color(template.getColor())
                        .system(false)
                        .build())
                .toList();
        categoryRepository.saveAll(categories);
    }

    private void seedPillarAllocations(Workspace workspace) {
        List<PillarAllocation> allocations = FIXED_TARGET_PERCENTS.entrySet().stream()
                .map(entry -> PillarAllocation.builder()
                        .id(new PillarAllocationId(workspace.getId(), entry.getKey()))
                        .targetPercent(entry.getValue())
                        .build())
                .toList();
        pillarAllocationRepository.saveAll(allocations);
    }
}
