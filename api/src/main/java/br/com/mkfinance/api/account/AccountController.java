package br.com.mkfinance.api.account;

import br.com.mkfinance.application.account.AccountService;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
 * All queries are scoped through {@link WorkspaceContext#getWorkspaceId()} (verified by
 * WorkspaceAccessInterceptor before this controller runs), not the raw {@code workspaceId} path
 * variable — the path variable is only declared so Spring MVC exposes it for the interceptor.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountMapper mapper;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<AccountResponse> list(
            @PathVariable UUID workspaceId, @RequestParam(defaultValue = "false") boolean includeArchived) {
        return accountService.list(workspaceContext.getWorkspaceId(), includeArchived).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody AccountRequest request) {
        return mapper.toResponse(accountService.create(workspaceContext.getWorkspaceId(), mapper.toInput(request)));
    }

    @PatchMapping("/{accountId}")
    public AccountResponse update(
            @PathVariable UUID workspaceId, @PathVariable UUID accountId, @Valid @RequestBody AccountRequest request) {
        return mapper.toResponse(
                accountService.update(workspaceContext.getWorkspaceId(), accountId, mapper.toInput(request)));
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId, @PathVariable UUID accountId) {
        accountService.delete(workspaceContext.getWorkspaceId(), accountId);
    }

    @PostMapping("/{accountId}/archive")
    public AccountResponse archive(@PathVariable UUID workspaceId, @PathVariable UUID accountId) {
        return mapper.toResponse(accountService.archive(workspaceContext.getWorkspaceId(), accountId));
    }
}
