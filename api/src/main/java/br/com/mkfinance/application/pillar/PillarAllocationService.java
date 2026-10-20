package br.com.mkfinance.application.pillar;

import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.domain.model.PillarAllocation;
import br.com.mkfinance.domain.model.PillarAllocationId;
import br.com.mkfinance.infrastructure.persistence.PillarAllocationRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-6: the six pillar targets, versioned by competency (V13). Reading resolves what was in force
 * for a given month; writing (PUT {@code /pillar-allocations}) always lands at the current month's
 * first day (PLANEJAMENTO §6.4 — the user's decision is that an edit applies from now on), leaving
 * every earlier competency untouched.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PillarAllocationService {

    private final PillarAllocationRepository pillarAllocationRepository;
    private final BusinessClock clock;

    /** The requested month, or the current one in America/Sao_Paulo when the client omits it (R7). */
    public YearMonth resolveMonth(YearMonth month) {
        return month != null ? month : YearMonth.from(clock.today());
    }

    @Transactional(readOnly = true)
    public List<PillarAllocation> list(UUID workspaceId, YearMonth month) {
        return pillarAllocationRepository.findEffectiveAt(workspaceId, month.atDay(1));
    }

    /**
     * Replaces the six targets from the current month onward. Validates the sum first (400
     * PILLAR_TARGETS_MUST_SUM_100) — a partial or duplicated set of pillars can sum to 100 while
     * silently leaving a pillar unedited, which the six-distinct-pillars check below catches with the
     * generic VALIDATION_ERROR (the contract's minItems/maxItems=6 does not by itself rule out
     * duplicates). Existing rows for the current competency are updated in place; missing ones are
     * inserted. Earlier competencies are never touched.
     */
    public List<PillarAllocation> update(UUID workspaceId, List<PillarAllocationInput> inputs) {
        validateSum(inputs);
        validateSixDistinctPillars(inputs);

        YearMonth currentMonth = YearMonth.from(clock.today());
        LocalDate effectiveFrom = currentMonth.atDay(1);

        List<PillarAllocation> saved = inputs.stream()
                .map(input -> {
                    PillarAllocationId id = new PillarAllocationId(workspaceId, input.pillar(), effectiveFrom);
                    PillarAllocation allocation = pillarAllocationRepository
                            .findById(id)
                            .orElseGet(() -> PillarAllocation.builder().id(id).build());
                    allocation.setTargetPercent((short) input.targetPercent());
                    return pillarAllocationRepository.save(allocation);
                })
                .toList();

        return saved.stream()
                .sorted(Comparator.comparing(pa -> pa.getId().getPillar()))
                .toList();
    }

    private void validateSum(List<PillarAllocationInput> inputs) {
        int sum = inputs.stream().mapToInt(PillarAllocationInput::targetPercent).sum();
        if (sum != 100) {
            throw AppException.pillarTargetsMustSum100(sum);
        }
    }

    private void validateSixDistinctPillars(List<PillarAllocationInput> inputs) {
        EnumSet<Pillar> given = EnumSet.noneOf(Pillar.class);
        for (PillarAllocationInput input : inputs) {
            given.add(input.pillar());
        }
        if (given.size() != Pillar.values().length) {
            throw AppException.of(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST);
        }
    }
}
