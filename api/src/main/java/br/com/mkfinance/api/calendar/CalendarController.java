package br.com.mkfinance.api.calendar;

import br.com.mkfinance.application.calendar.CalendarService;
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
 * Contract tag {@code calendar}. Read-only: reading the month is what lazily materializes
 * recurrence occurrences into PENDING transactions (CT-1) — see {@code CalendarService}. Like every
 * workspace-scoped controller, queries use {@link WorkspaceContext#getWorkspaceId()} — already
 * verified by {@code WorkspaceAccessInterceptor} — rather than the raw path variable, which is only
 * declared so Spring MVC exposes it to the interceptor.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final CalendarMapper mapper;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public CalendarMonthResponse get(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        YearMonth resolved = calendarService.resolveMonth(month);
        return mapper.toResponse(resolved, calendarService.entriesOf(workspaceContext.getWorkspaceId(), resolved));
    }
}
