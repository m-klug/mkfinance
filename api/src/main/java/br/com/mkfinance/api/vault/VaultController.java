package br.com.mkfinance.api.vault;

import br.com.mkfinance.application.transaction.AccountBalanceService;
import br.com.mkfinance.application.vault.VaultBalanceService;
import br.com.mkfinance.application.vault.VaultLedgerService;
import br.com.mkfinance.application.vault.VaultReallocationResult;
import br.com.mkfinance.application.vault.VaultReallocationService;
import br.com.mkfinance.application.vault.VaultService;
import br.com.mkfinance.domain.model.Vault;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.domain.service.VaultBalanceCalculator.Composition;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import br.com.mkfinance.infrastructure.security.WorkspaceContext;

/**
 * Contract tag {@code vaults} — CRUD, archive and the account composition (CT-2, BE-9). Every
 * query uses {@link WorkspaceContext#getWorkspaceId()}, already verified by
 * {@code WorkspaceAccessInterceptor}, the same pattern every other workspace-scoped controller
 * follows.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}")
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;
    private final VaultBalanceService vaultBalanceService;
    private final VaultLedgerService vaultLedgerService;
    private final VaultReallocationService vaultReallocationService;
    private final AccountBalanceService accountBalanceService;
    private final VaultMapper mapper;
    private final WorkspaceContext workspaceContext;

    /**
     * The account's whole composition in one request (CT-2 decision 1) — every vault plus the
     * unallocated slice, with the Σ vaults + unallocated = account balance identity already checked.
     */
    @GetMapping("/accounts/{accountId}/vaults")
    public VaultCompositionResponse list(
            @PathVariable UUID workspaceId,
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        UUID scope = workspaceContext.getWorkspaceId();
        List<Vault> vaults = vaultService.list(scope, accountId, includeArchived);
        Balance accountBalance = accountBalanceService.balanceOf(scope, accountId);
        Composition composition = vaultBalanceService.compositionOf(scope, accountBalance, vaults);
        return mapper.toCompositionResponse(accountId, vaults, composition);
    }

    @PostMapping("/accounts/{accountId}/vaults")
    @ResponseStatus(HttpStatus.CREATED)
    public VaultResponse create(
            @PathVariable UUID workspaceId, @PathVariable UUID accountId, @Valid @RequestBody VaultRequest request) {
        Vault vault = vaultService.create(workspaceContext.getWorkspaceId(), accountId, mapper.toInput(request));
        // A brand-new vault has no ledger history, so both balances are zero by definition.
        return mapper.toResponse(vault, new Balance(0L, 0L));
    }

    @PatchMapping("/vaults/{vaultId}")
    public VaultResponse update(
            @PathVariable UUID workspaceId, @PathVariable UUID vaultId, @Valid @RequestBody VaultRequest request) {
        UUID scope = workspaceContext.getWorkspaceId();
        Vault vault = vaultService.update(scope, vaultId, mapper.toInput(request));
        return mapper.toResponse(vault, vaultBalanceService.balanceOf(scope, vaultId));
    }

    @DeleteMapping("/vaults/{vaultId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId, @PathVariable UUID vaultId) {
        vaultService.delete(workspaceContext.getWorkspaceId(), vaultId);
    }

    @PostMapping("/vaults/{vaultId}/archive")
    public VaultResponse archive(@PathVariable UUID workspaceId, @PathVariable UUID vaultId) {
        UUID scope = workspaceContext.getWorkspaceId();
        Vault vault = vaultService.archive(scope, vaultId);
        return mapper.toResponse(vault, vaultBalanceService.balanceOf(scope, vaultId));
    }

    /** The vault's own ledger, most recent first. */
    @GetMapping("/vaults/{vaultId}/entries")
    public List<VaultEntryResponse> entries(
            @PathVariable UUID workspaceId,
            @PathVariable UUID vaultId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        UUID scope = workspaceContext.getWorkspaceId();
        vaultService.getOwned(scope, vaultId); // 404 guard — the vault must exist in this workspace
        return vaultLedgerService.listEntries(vaultId, from, to).stream()
                .map(mapper::toEntryResponse)
                .toList();
    }

    /** Either side may be null, meaning the unallocated slice (§4.7) — both null is VALIDATION_ERROR. */
    @PostMapping("/vaults/reallocations")
    @ResponseStatus(HttpStatus.CREATED)
    public VaultReallocationResponse reallocate(
            @PathVariable UUID workspaceId, @Valid @RequestBody VaultReallocationRequest request) {
        VaultReallocationResult result =
                vaultReallocationService.create(workspaceContext.getWorkspaceId(), mapper.toInput(request));
        return mapper.toResponse(result);
    }

    /** Undoes both sides at once — a half-undone reallocation would not add up. */
    @DeleteMapping("/vaults/reallocations/{reallocationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReallocation(@PathVariable UUID workspaceId, @PathVariable UUID reallocationId) {
        vaultReallocationService.delete(workspaceContext.getWorkspaceId(), reallocationId);
    }
}
