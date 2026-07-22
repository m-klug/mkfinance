package br.com.mkfinance.api.pillar;

import br.com.mkfinance.application.category.PillarAllocationService;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspaces/{workspaceId}/pillar-allocations")
@RequiredArgsConstructor
public class PillarAllocationController {

    private final PillarAllocationService pillarAllocationService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<PillarAllocationResponse> list(@PathVariable UUID workspaceId) {
        return pillarAllocationService.list(workspaceContext.getWorkspaceId()).stream()
                .map(pa -> new PillarAllocationResponse(pa.getId().getPillar(), pa.getTargetPercent()))
                .toList();
    }
}
