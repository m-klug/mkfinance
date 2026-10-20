package br.com.mkfinance.api.investment;

import br.com.mkfinance.application.investment.FixedIncomePositionService;
import br.com.mkfinance.application.investment.InvestmentSummaryService;
import br.com.mkfinance.application.investment.PositionSettlementInput;
import br.com.mkfinance.domain.model.PositionStatus;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tag {@code investments} of the contract (DESENVOLVIMENTO-POSICOES.md). */
@RestController
@RequestMapping("/workspaces/{workspaceId}/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private final FixedIncomePositionService service;
    private final InvestmentSummaryService summaryService;
    private final PositionMapper mapper;
    private final WorkspaceContext workspaceContext;

    /**
     * Net worth as of today: every account's balance plus the yield the active positions have accrued
     * and not yet received, with the card bills still owed shown beside it and never subtracted.
     */
    @GetMapping("/summary")
    public InvestmentSummaryResponse summary(@PathVariable UUID workspaceId) {
        return InvestmentSummaryResponse.of(summaryService.summary(workspaceContext.getWorkspaceId()));
    }

    /** Purchases (transfers into an investment account) that no position claims yet. */
    @GetMapping("/unlinked-contributions")
    public List<UnlinkedContributionResponse> unlinkedContributions(@PathVariable UUID workspaceId) {
        UUID scope = workspaceContext.getWorkspaceId();
        return mapper.toUnlinked(scope, service.unlinkedContributions(scope));
    }

    @GetMapping("/positions")
    public List<PositionResponse> list(
            @PathVariable UUID workspaceId, @RequestParam(required = false) PositionStatus status) {
        UUID scope = workspaceContext.getWorkspaceId();
        return mapper.toResponses(scope, service.list(scope, status));
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public PositionResponse create(@PathVariable UUID workspaceId, @Valid @RequestBody PositionRequest request) {
        UUID scope = workspaceContext.getWorkspaceId();
        return mapper.toResponse(scope, service.create(scope, mapper.toInput(request)));
    }

    @GetMapping("/positions/{positionId}")
    public PositionResponse get(@PathVariable UUID workspaceId, @PathVariable UUID positionId) {
        UUID scope = workspaceContext.getWorkspaceId();
        return mapper.toResponse(scope, service.get(scope, positionId));
    }

    @PutMapping("/positions/{positionId}")
    public PositionResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID positionId,
            @Valid @RequestBody PositionUpdateRequest request) {
        UUID scope = workspaceContext.getWorkspaceId();
        return mapper.toResponse(scope, service.update(scope, positionId, mapper.toUpdate(request)));
    }

    /**
     * Books the payment of a title: the real yield goes to the position's vault (or stays unallocated)
     * and the position remembers what was received next to what was estimated. Records no transfer.
     */
    @PostMapping("/positions/{positionId}/settle")
    public PositionResponse settle(
            @PathVariable UUID workspaceId,
            @PathVariable UUID positionId,
            @Valid @RequestBody PositionSettleRequest request) {
        UUID scope = workspaceContext.getWorkspaceId();
        return mapper.toResponse(scope, service.settle(scope, positionId, new PositionSettlementInput(
                request.settledDate(), request.receivedCents(), request.returnTransferTransactionId())));
    }

    /** Undoes the payment: removes the booked yield and returns the position to active. */
    @PostMapping("/positions/{positionId}/unsettle")
    public PositionResponse unsettle(@PathVariable UUID workspaceId, @PathVariable UUID positionId) {
        UUID scope = workspaceContext.getWorkspaceId();
        return mapper.toResponse(scope, service.unsettle(scope, positionId));
    }

    /** Links, or with a null id unlinks, the transfer back to checking after the title was paid. */
    @PutMapping("/positions/{positionId}/return-transfer")
    public PositionResponse linkReturnTransfer(
            @PathVariable UUID workspaceId,
            @PathVariable UUID positionId,
            @RequestBody PositionReturnTransferRequest request) {
        UUID scope = workspaceContext.getWorkspaceId();
        return mapper.toResponse(scope, service.linkReturnTransfer(scope, positionId, request.returnTransferTransactionId()));
    }

    @DeleteMapping("/positions/{positionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId, @PathVariable UUID positionId) {
        service.delete(workspaceContext.getWorkspaceId(), positionId);
    }
}
