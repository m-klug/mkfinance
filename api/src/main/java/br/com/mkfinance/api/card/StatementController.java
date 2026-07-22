package br.com.mkfinance.api.card;

import br.com.mkfinance.application.card.StatementService;
import br.com.mkfinance.application.card.StatementView;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Statements: listing a card's bills, opening one, and paying it (contract tag {@code statements}). */
@RestController
@RequestMapping("/workspaces/{workspaceId}")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService statementService;
    private final CardMapper mapper;
    private final WorkspaceContext workspaceContext;

    /**
     * Statements of a card. {@code from}/{@code to} are reference months ({@code YYYY-MM}),
     * inclusive; months in the range that have no statement yet are materialized on the way out (R3).
     */
    @GetMapping("/cards/{cardId}/statements")
    public List<StatementResponse> list(
            @PathVariable UUID workspaceId,
            @PathVariable UUID cardId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth to) {
        return statementService.list(workspaceContext.getWorkspaceId(), cardId, from, to).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @GetMapping("/statements/{statementId}")
    public StatementDetailResponse get(@PathVariable UUID workspaceId, @PathVariable UUID statementId) {
        return mapper.toResponse(statementService.detail(workspaceContext.getWorkspaceId(), statementId));
    }

    /**
     * Pays all or part of the statement, debiting the chosen account (R2). Returns the statement with
     * its refreshed status: PAID when nothing is left, PARTIAL otherwise.
     */
    @PostMapping("/statements/{statementId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public StatementResponse pay(
            @PathVariable UUID workspaceId,
            @PathVariable UUID statementId,
            @Valid @RequestBody StatementPaymentRequest request) {
        StatementView view =
                statementService.pay(workspaceContext.getWorkspaceId(), statementId, mapper.toInput(request));
        return mapper.toResponse(view);
    }

    /**
     * Reverses one payment, removing the debit it made on the account along with it. Works on a PAID
     * statement too — that is the point: the status falls back to PARTIAL or OPEN on its own, since
     * it is derived from the payments that remain.
     */
    @DeleteMapping("/statements/{statementId}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reversePayment(
            @PathVariable UUID workspaceId, @PathVariable UUID statementId, @PathVariable UUID paymentId) {
        statementService.reversePayment(workspaceContext.getWorkspaceId(), statementId, paymentId);
    }
}
