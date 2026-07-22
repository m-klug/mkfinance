package br.com.mkfinance.api.card;

import br.com.mkfinance.application.card.CardPurchaseService;
import br.com.mkfinance.domain.model.CardPurchase;
import br.com.mkfinance.domain.model.Installment;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Editing and deleting an existing purchase (contract tag {@code cards}). Both refuse with
 * {@code STATEMENT_IMMUTABLE} when they would disturb a statement already paid in full (R6).
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/purchases/{purchaseId}")
@RequiredArgsConstructor
public class CardPurchaseController {

    private final CardPurchaseService purchaseService;
    private final CardMapper mapper;
    private final WorkspaceContext workspaceContext;

    @PatchMapping
    public CardPurchaseResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID purchaseId,
            @Valid @RequestBody CardPurchaseRequest request) {
        CardPurchase purchase =
                purchaseService.update(workspaceContext.getWorkspaceId(), purchaseId, mapper.toInput(request));
        List<Installment> installments = purchaseService.installmentsOf(purchase);
        return mapper.toResponse(purchase, installments, purchaseService.statementMonthsOf(installments));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId, @PathVariable UUID purchaseId) {
        purchaseService.delete(workspaceContext.getWorkspaceId(), purchaseId);
    }
}
