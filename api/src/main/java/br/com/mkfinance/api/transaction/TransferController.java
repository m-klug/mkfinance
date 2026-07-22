package br.com.mkfinance.api.transaction;

import br.com.mkfinance.application.transaction.TransferService;
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
 * Tag {@code transfers} of the contract — creating the mirrored pair (R4), including the
 * contribution case (R9).
 *
 * <p>Editing, settling and deleting a transfer go through the {@code transactions} endpoints on
 * either leg; those propagate to the sibling automatically, so there is no PATCH/DELETE here.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final TransactionMapper mapper;
    private final WorkspaceContext workspaceContext;

    /** Returns the outgoing leg, carrying the contribution pillar that was actually applied. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody TransferRequest request) {
        return mapper.toResponse(transferService.create(workspaceContext.getWorkspaceId(), mapper.toInput(request)));
    }
}
