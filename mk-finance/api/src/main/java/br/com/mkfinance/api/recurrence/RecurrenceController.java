package br.com.mkfinance.api.recurrence;

import br.com.mkfinance.application.recurrence.RecurrenceRuleService;
import br.com.mkfinance.application.recurrence.RecurrenceScope;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract tag {@code recurrences}. Like every workspace-scoped controller, queries use
 * {@link WorkspaceContext#getWorkspaceId()} — already verified by {@code WorkspaceAccessInterceptor}
 * — rather than the raw path variable, which is only declared so Spring MVC exposes it to the
 * interceptor.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/recurrences")
@RequiredArgsConstructor
public class RecurrenceController {

    private final RecurrenceRuleService recurrenceRuleService;
    private final RecurrenceMapper mapper;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<RecurrenceResponse> list(
            @PathVariable UUID workspaceId, @RequestParam(required = false) Boolean active) {
        return recurrenceRuleService.list(workspaceContext.getWorkspaceId(), active).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurrenceResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody RecurrenceRequest request) {
        return mapper.toResponse(recurrenceRuleService.create(workspaceContext.getWorkspaceId(), mapper.toInput(request)));
    }

    @PatchMapping("/{recurrenceId}")
    public RecurrenceResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID recurrenceId,
            @RequestParam(defaultValue = "THIS_AND_FUTURE") RecurrenceScope scope,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurrenceDate,
            @Valid @RequestBody RecurrenceRequest request) {
        return mapper.toResponse(recurrenceRuleService.update(
                workspaceContext.getWorkspaceId(), recurrenceId, scope, occurrenceDate, mapper.toInput(request)));
    }

    @DeleteMapping("/{recurrenceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID workspaceId,
            @PathVariable UUID recurrenceId,
            @RequestParam(defaultValue = "THIS_AND_FUTURE") RecurrenceScope scope,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurrenceDate) {
        recurrenceRuleService.delete(workspaceContext.getWorkspaceId(), recurrenceId, scope, occurrenceDate);
    }
}
