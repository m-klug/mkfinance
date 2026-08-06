package br.com.mkfinance.api.transaction;

import br.com.mkfinance.application.tag.TagService;
import br.com.mkfinance.application.transaction.TransactionFilter;
import br.com.mkfinance.application.transaction.TransactionService;
import br.com.mkfinance.application.transaction.TransferService;
import br.com.mkfinance.domain.model.Tag;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Tag {@code transactions} of the contract.
 *
 * <p>Like every workspace-scoped controller, queries use {@link WorkspaceContext#getWorkspaceId()}
 * — already verified by {@code WorkspaceAccessInterceptor} — rather than the raw path variable,
 * which is only declared so Spring MVC exposes it to the interceptor.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    /** Contract caps {@code size} at 200; a client asking for more gets 200, not an error. */
    private static final int MAX_PAGE_SIZE = 200;

    private final TransactionService transactionService;
    private final TransactionMapper mapper;
    private final TagService tagService;
    private final TransferService transferService;
    private final WorkspaceContext workspaceContext;

    @GetMapping
    public TransactionPageResponse list(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TransactionFilter filter = new TransactionFilter(from, to, accountId, categoryId, type, status, search, tags);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Page<Transaction> result = transactionService.list(workspaceContext.getWorkspaceId(), filter, pageable);
        List<UUID> transactionIds = result.getContent().stream().map(Transaction::getId).toList();
        Map<UUID, List<Tag>> tagsByTransaction = tagService.tagsOf(transactionIds);
        Map<UUID, UUID> vaultIdByTransaction = transferService.vaultIdsOf(transactionIds);
        return mapper.toPageResponse(result, tagsByTransaction, vaultIdByTransaction);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(
            @PathVariable UUID workspaceId, @Valid @RequestBody TransactionRequest request) {
        Transaction transaction =
                transactionService.create(workspaceContext.getWorkspaceId(), mapper.toInput(request));
        return mapper.toResponse(transaction, tagService.tagsOf(transaction.getId()), vaultIdOf(transaction.getId()));
    }

    @PatchMapping("/{transactionId}")
    public TransactionResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID transactionId,
            @Valid @RequestBody TransactionRequest request) {
        Transaction transaction = transactionService.update(
                workspaceContext.getWorkspaceId(), transactionId, mapper.toInput(request));
        return mapper.toResponse(transaction, tagService.tagsOf(transaction.getId()), vaultIdOf(transaction.getId()));
    }

    @DeleteMapping("/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID workspaceId, @PathVariable UUID transactionId) {
        transactionService.delete(workspaceContext.getWorkspaceId(), transactionId);
    }

    /** Efetivar em um clique (R5). */
    @PostMapping("/{transactionId}/settle")
    public TransactionResponse settle(@PathVariable UUID workspaceId, @PathVariable UUID transactionId) {
        Transaction transaction = transactionService.settle(workspaceContext.getWorkspaceId(), transactionId);
        return mapper.toResponse(transaction, tagService.tagsOf(transaction.getId()), vaultIdOf(transaction.getId()));
    }

    /** Single-row vault lookup (create/update/settle responses) built on the same batch query as {@link #list}. */
    private UUID vaultIdOf(UUID transactionId) {
        return transferService.vaultIdsOf(List.of(transactionId)).get(transactionId);
    }
}
