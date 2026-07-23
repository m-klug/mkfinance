package br.com.mkfinance.api.dashboard;

import br.com.mkfinance.application.dashboard.DashboardService;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract tag {@code dashboard}. Like every workspace-scoped controller, queries use
 * {@link WorkspaceContext#getWorkspaceId()} — already verified by {@code WorkspaceAccessInterceptor}
 * — rather than the raw path variable, which is only declared so Spring MVC exposes it to the
 * interceptor.
 *
 * <p>{@code month} is {@code YYYY-MM}; when omitted it defaults to the current month in
 * America/Sao_Paulo (R7).
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final DashboardMapper mapper;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        UUID scope = workspaceContext.getWorkspaceId();
        return mapper.toResponse(dashboardService.summary(scope, dashboardService.resolveMonth(month)));
    }

    @GetMapping("/pillar-allocation")
    public PillarAllocationReportResponse pillarAllocation(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        UUID scope = workspaceContext.getWorkspaceId();
        YearMonth resolved = dashboardService.resolveMonth(month);
        return mapper.toResponse(resolved, dashboardService.pillarAllocation(scope, resolved));
    }
}
