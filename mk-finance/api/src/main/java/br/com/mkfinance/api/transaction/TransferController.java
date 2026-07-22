package br.com.mkfinance.api.transaction;

import br.com.mkfinance.application.transaction.TransferService;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * <p>Editing goes through {@code PATCH /transfers/{transactionId}} on either leg, which has its own
 * payload: a transfer carries no category and cannot change type, so the contract's
 * {@code TransactionRequest} — which requires a {@code categoryId} and only enumerates
 * INCOME/EXPENSE — cannot express one. Settling and deleting still go through the
 * {@code transactions} endpoints on either leg, since those need no payload and already propagate
 * to the sibling.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;
    private final TransferUpdateHandler updateHandler;
    private final TransactionMapper mapper;
    private final WorkspaceContext workspaceContext;

    /** Returns the outgoing leg, carrying the contribution pillar that was actually applied. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody TransferRequest request) {
        return mapper.toResponse(transferService.create(workspaceContext.getWorkspaceId(), mapper.toInput(request)));
    }

    /**
     * Edits both legs atomically through either one. Returns the leg named in the path — not always
     * the outgoing one — since that is the row the caller was looking at.
     */
    @PatchMapping("/{transactionId}")
    public TransactionResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransferUpdateRequest request) {
        return updateHandler.update(workspaceContext.getWorkspaceId(), transactionId, request);
    }
}
