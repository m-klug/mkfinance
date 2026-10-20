package br.com.mkfinance.api.card;

import br.com.mkfinance.application.card.CardPurchaseService;
import br.com.mkfinance.application.card.CardService;
import br.com.mkfinance.application.card.statementimport.StatementImportService;
import br.com.mkfinance.domain.model.Card;
import br.com.mkfinance.domain.model.CardPurchase;
import br.com.mkfinance.domain.model.Installment;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cards and the purchases charged to them (contract tag {@code cards}).
 *
 * <p>Like every workspace-scoped controller, the queries use
 * {@link WorkspaceContext#getWorkspaceId()} — already verified by {@code WorkspaceAccessInterceptor}
 * — rather than the {@code workspaceId} path variable, which is declared only so the interceptor
 * sees it.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final CardPurchaseService purchaseService;
    private final StatementImportService importService;
    private final CardMapper mapper;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public List<CardResponse> list(
            @PathVariable UUID workspaceId, @RequestParam(defaultValue = "false") boolean includeArchived) {
        List<Card> cards = cardService.list(workspaceContext.getWorkspaceId(), includeArchived);
        Map<UUID, Long> availableLimits = cardService.availableLimitByCard(cards);
        return cards.stream()
                .map(card -> mapper.toResponse(card, availableLimits.get(card.getId())))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody CardRequest request) {
        Card card = cardService.create(workspaceContext.getWorkspaceId(), mapper.toInput(request));
        // A brand-new card has no statements, so its whole limit is available.
        return mapper.toResponse(card, card.getLimitCents());
    }

    @PatchMapping("/{cardId}")
    public CardResponse update(
            @PathVariable UUID workspaceId, @PathVariable UUID cardId, @Valid @RequestBody CardRequest request) {
        Card card = cardService.update(workspaceContext.getWorkspaceId(), cardId, mapper.toInput(request));
        return mapper.toResponse(card, cardService.availableLimitByCard(List.of(card)).get(card.getId()));
    }

    /** Idempotent, mirroring {@code POST /accounts/{accountId}/archive}. */
    @PostMapping("/{cardId}/archive")
    public CardResponse archive(@PathVariable UUID workspaceId, @PathVariable UUID cardId) {
        Card card = cardService.archive(workspaceContext.getWorkspaceId(), cardId);
        return mapper.toResponse(card, cardService.availableLimitByCard(List.of(card)).get(card.getId()));
    }

    @PostMapping("/{cardId}/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public CardPurchaseResponse createPurchase(
            @PathVariable UUID workspaceId,
            @PathVariable UUID cardId,
            @Valid @RequestBody CardPurchaseRequest request) {
        CardPurchase purchase =
                purchaseService.create(workspaceContext.getWorkspaceId(), cardId, mapper.toInput(request));
        return withInstallments(purchase);
    }

    /** All-or-nothing: one refused item (paid statement, wrong category type) creates none of them. */
    @PostMapping("/{cardId}/purchases/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CardPurchaseResponse> createPurchases(
            @PathVariable UUID workspaceId,
            @PathVariable UUID cardId,
            @Valid @RequestBody CardPurchaseBatchRequest request) {
        return purchaseService
                .createBatch(workspaceContext.getWorkspaceId(), cardId, mapper.toInputs(request))
                .stream()
                .map(this::withInstallments)
                .toList();
    }

    /**
     * Reads a statement PDF and returns what it would import; writes nothing. The password travels
     * in the form body (never the URL) and is not stored or logged.
     */
    @PostMapping(path = "/{cardId}/statement-imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StatementImportPreviewResponse previewStatementImport(
            @PathVariable UUID workspaceId,
            @PathVariable UUID cardId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "") String password)
            throws IOException {
        return mapper.toResponse(importService.preview(
                workspaceContext.getWorkspaceId(), cardId, file.getBytes(), password));
    }

    private CardPurchaseResponse withInstallments(CardPurchase purchase) {
        List<Installment> installments = purchaseService.installmentsOf(purchase);
        return mapper.toResponse(purchase, installments, purchaseService.statementMonthsOf(installments));
    }
}
