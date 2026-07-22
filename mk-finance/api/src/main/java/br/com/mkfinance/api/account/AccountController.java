package br.com.mkfinance.api.account;

import br.com.mkfinance.application.account.AccountService;
import br.com.mkfinance.application.transaction.AccountBalanceService;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
    private final AccountBalanceService balanceService;
    private final AccountMapper mapper;
    private final WorkspaceContext workspaceContext;

    /**
     * Balances come from one aggregated query for the whole workspace, not one per account — the
     * same shape as {@code CardController.list} and its available limits. Listing a hundred accounts
     * costs two queries, not a hundred and one.
     */
    @GetMapping
    public List<AccountResponse> list(
            @PathVariable UUID workspaceId, @RequestParam(defaultValue = "false") boolean includeArchived) {
        UUID scope = workspaceContext.getWorkspaceId();
        Map<UUID, Balance> balances = balanceService.balancesByAccount(scope);
        return accountService.list(scope, includeArchived).stream()
                .map(account -> mapper.toResponse(account, balanceOf(balances, account)))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody AccountRequest request) {
        Account account = accountService.create(workspaceContext.getWorkspaceId(), mapper.toInput(request));
        // A brand-new account has no history, so both balances are its initial balance by definition.
        return mapper.toResponse(account, Balance.ofInitial(account.getInitialBalanceCents()));
    }

    @PatchMapping("/{accountId}")
    public AccountResponse update(
            @PathVariable UUID workspaceId, @PathVariable UUID accountId, @Valid @RequestBody AccountRequest request) {
        UUID scope = workspaceContext.getWorkspaceId();
        Account account = accountService.update(scope, accountId, mapper.toInput(request));
        return mapper.toResponse(account, balanceService.balanceOf(scope, account.getId()));
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId, @PathVariable UUID accountId) {
        accountService.delete(workspaceContext.getWorkspaceId(), accountId);
    }

    @PostMapping("/{accountId}/archive")
    public AccountResponse archive(@PathVariable UUID workspaceId, @PathVariable UUID accountId) {
        UUID scope = workspaceContext.getWorkspaceId();
        Account account = accountService.archive(scope, accountId);
        return mapper.toResponse(account, balanceService.balanceOf(scope, account.getId()));
    }

    /** An account the aggregate query never saw simply has no movements yet. */
    private Balance balanceOf(Map<UUID, Balance> balances, Account account) {
        Balance balance = balances.get(account.getId());
        return balance != null ? balance : Balance.ofInitial(account.getInitialBalanceCents());
    }
}
