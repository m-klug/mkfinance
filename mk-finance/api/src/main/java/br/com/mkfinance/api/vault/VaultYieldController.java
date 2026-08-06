package br.com.mkfinance.api.vault;

import br.com.mkfinance.application.vault.VaultYieldResult;
import br.com.mkfinance.application.vault.VaultYieldService;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract tag {@code vaults}, single endpoint {@code createVaultYield} (BE-11,
 * DESENVOLVIMENTO-CAIXINHAS.md §4.5). No delete endpoint — the booked yield is an ordinary
 * transaction, undone through {@code DELETE /transactions/{transactionId}}, whose cascade removes
 * the split with it.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/accounts/{accountId}/vault-yields")
@RequiredArgsConstructor
public class VaultYieldController {

    private final VaultYieldService vaultYieldService;
    private final VaultYieldMapper mapper;
    private final WorkspaceContext workspaceContext;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VaultYieldResponse create(
            @PathVariable UUID workspaceId, @PathVariable UUID accountId, @Valid @RequestBody VaultYieldRequest request) {
        VaultYieldResult result =
                vaultYieldService.create(workspaceContext.getWorkspaceId(), accountId, mapper.toInput(request));
        return mapper.toResponse(result);
    }
}
