package br.com.mkfinance.api.pillar;

import br.com.mkfinance.application.pillar.PillarAllocationInput;
import br.com.mkfinance.application.pillar.PillarAllocationService;
import br.com.mkfinance.domain.model.PillarAllocation;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract tag {@code pillars}. {@code month} is {@code YYYY-MM}, defaulting to the current month in
 * America/Sao_Paulo (R7) — the targets in force for that competency (BE-6, V13), never rewritten by
 * a later edit. PUT always writes at the current month's first day regardless of any {@code month}
 * the client might otherwise infer, since editing only ever means "from now on".
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/pillar-allocations")
@RequiredArgsConstructor
public class PillarAllocationController {

    private final PillarAllocationService pillarAllocationService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<PillarAllocationResponse> list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        UUID scope = workspaceContext.getWorkspaceId();
        YearMonth resolved = pillarAllocationService.resolveMonth(month);
        return pillarAllocationService.list(scope, resolved).stream().map(this::toResponse).toList();
    }

    @PutMapping
    public List<PillarAllocationResponse> update(
            @PathVariable UUID workspaceId, @Valid @RequestBody PillarAllocationsRequest request) {
        UUID scope = workspaceContext.getWorkspaceId();
        List<PillarAllocationInput> inputs = request.allocations().stream()
                .map(item -> new PillarAllocationInput(item.pillar(), item.targetPercent()))
                .toList();
        return pillarAllocationService.update(scope, inputs).stream()
                .map(this::toResponse)
                .toList();
    }

    private PillarAllocationResponse toResponse(PillarAllocation allocation) {
        return new PillarAllocationResponse(
                allocation.getId().getPillar(), allocation.getTargetPercent(), allocation.getId().getEffectiveFrom());
    }
}
