package br.com.mkfinance.api.reimbursement;

import br.com.mkfinance.api.transaction.TransactionMapper;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.application.reimbursement.ReimbursementService;
import br.com.mkfinance.domain.model.Reimbursement;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tag {@code reimbursements} of the contract (ADR-0002). */
@RestController
@RequestMapping("/workspaces/{workspaceId}")
@RequiredArgsConstructor
public class ReimbursementController {

    private final ReimbursementService reimbursementService;
    private final TransactionMapper transactionMapper;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/transactions/{transactionId}/reimbursements")
    public List<ReimbursementResponse> list(@PathVariable UUID workspaceId, @PathVariable UUID transactionId) {
        return reimbursementService.listFor(workspaceContext.getWorkspaceId(), transactionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/transactions/{incomeId}/reimbursements")
    @ResponseStatus(HttpStatus.CREATED)
    public ReimbursementResponse create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID incomeId,
            @Valid @RequestBody ReimbursementRequest request) {
        return toResponse(reimbursementService.link(
                workspaceContext.getWorkspaceId(), incomeId, request.expenseTransactionId(), request.amountCents()));
    }

    @DeleteMapping("/reimbursements/{reimbursementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId, @PathVariable UUID reimbursementId) {
        reimbursementService.unlink(workspaceContext.getWorkspaceId(), reimbursementId);
    }

    /** Expenses still waiting for money, oldest first — the "link to expenses" dialog's list. */
    @GetMapping("/transactions/reimbursable-pending")
    public List<TransactionResponse> pending(@PathVariable UUID workspaceId) {
        List<Transaction> expenses = reimbursementService.pendingExpenses(workspaceContext.getWorkspaceId());
        Map<UUID, Long> linked = reimbursementService.linkedTotals(
                expenses.stream().map(Transaction::getId).toList());
        return expenses.stream()
                .map(t -> transactionMapper.toResponse(t, List.of(), null, linked.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    private ReimbursementResponse toResponse(Reimbursement r) {
        return new ReimbursementResponse(
                r.getId(), r.getIncomeTransactionId(), r.getExpenseTransactionId(), r.getAmountCents(), r.getCreatedAt());
    }
}
