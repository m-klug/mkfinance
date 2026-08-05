package br.com.mkfinance.application.export;

import br.com.mkfinance.application.tag.TagService;
import br.com.mkfinance.application.transaction.TransactionFilter;
import br.com.mkfinance.application.transaction.TransactionService;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.Category;
import br.com.mkfinance.domain.model.Tag;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.CategoryRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports the extrato as CSV for Excel pt-BR (PLANEJAMENTO §6.6): UTF-8 with a BOM so Excel detects
 * the encoding, and {@code ';'} as the field separator so the comma stays free for decimals — a
 * Brazilian Excel reads {@code 1500,00} as a number only when the columns are split on {@code ';'}.
 *
 * <p>Rows come from BE-3's own read path ({@link TransactionService#list}, same filters and same
 * newest-first order as the on-screen extrato), page by page, so nothing loads the whole history into
 * memory at once. Account and category ids are resolved to names through two small lookups; tags
 * (BE-8) are batch-loaded per page the same way {@code TransactionController} does for the extrato.
 *
 * <p>Column headers are kept in English: the backend does not emit localized copy (PLANEJAMENTO
 * §3.3). Category and account <em>names</em> in the data are user content and stay as the user typed
 * them.
 */
@Service
@RequiredArgsConstructor
public class TransactionCsvExporter {

    private static final char SEPARATOR = ';';
    /** Joins the tags of one row inside a single column — never conflicts with {@link #SEPARATOR}. */
    private static final String TAG_JOIN = ",";
    private static final String LINE_END = "\r\n";
    /** Excel reads a file starting with this BOM as UTF-8, so accented category names are not mojibake. */
    private static final String BOM = "﻿";
    private static final int PAGE_SIZE = 500;
    private static final String[] HEADER = {
        "date", "type", "direction", "description", "category", "account", "amount", "status", "note", "tags"
    };

    private final TransactionService transactionService;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TagService tagService;

    @Transactional(readOnly = true)
    public byte[] export(UUID workspaceId, LocalDate from, LocalDate to) {
        Map<UUID, String> accountNames = accountNames(workspaceId);
        Map<UUID, String> categoryNames = categoryNames(workspaceId);
        TransactionFilter filter = new TransactionFilter(from, to, null, null, null, null, null, null);

        StringBuilder csv = new StringBuilder(BOM);
        appendRow(csv, HEADER);

        int page = 0;
        Page<Transaction> pageResult;
        do {
            pageResult = transactionService.list(workspaceId, filter, PageRequest.of(page, PAGE_SIZE));
            Map<UUID, List<Tag>> tagsByTransaction = tagService.tagsOf(
                    pageResult.getContent().stream().map(Transaction::getId).toList());
            for (Transaction transaction : pageResult.getContent()) {
                appendRow(csv, row(transaction, accountNames, categoryNames, tagsByTransaction));
            }
            page++;
        } while (page < pageResult.getTotalPages());

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String[] row(
            Transaction transaction,
            Map<UUID, String> accountNames,
            Map<UUID, String> categoryNames,
            Map<UUID, List<Tag>> tagsByTransaction) {
        return new String[] {
            transaction.getDate().toString(),
            transaction.getType().name(),
            transaction.getTransferDirection() != null ? transaction.getTransferDirection().name() : "",
            nullToEmpty(transaction.getDescription()),
            transaction.getCategoryId() != null ? categoryNames.getOrDefault(transaction.getCategoryId(), "") : "",
            accountNames.getOrDefault(transaction.getAccountId(), ""),
            reais(transaction.getAmountCents()),
            transaction.getStatus().name(),
            nullToEmpty(transaction.getNote()),
            tagList(tagsByTransaction.getOrDefault(transaction.getId(), List.of()))
        };
    }

    private String tagList(List<Tag> tags) {
        return tags.stream()
                .map(Tag::getName)
                .sorted(Comparator.naturalOrder())
                .reduce((a, b) -> a + TAG_JOIN + b)
                .orElse("");
    }

    private Map<UUID, String> accountNames(UUID workspaceId) {
        Map<UUID, String> names = new HashMap<>();
        for (Account account : accountRepository.findByWorkspaceIdOrderByNameAsc(workspaceId)) {
            names.put(account.getId(), account.getName());
        }
        return names;
    }

    private Map<UUID, String> categoryNames(UUID workspaceId) {
        Map<UUID, String> names = new HashMap<>();
        for (Category category : categoryRepository.findByWorkspaceIdOrderByNameAsc(workspaceId)) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    /** Cents to reais with a comma decimal, e.g. {@code 150000 -> "1500,00"}; amounts are non-negative. */
    private String reais(long amountCents) {
        return (amountCents / 100) + "," + String.format("%02d", amountCents % 100);
    }

    private void appendRow(StringBuilder csv, String[] fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                csv.append(SEPARATOR);
            }
            csv.append(escape(fields[i]));
        }
        csv.append(LINE_END);
    }

    /** RFC-4180 quoting, needed whenever a value could contain the separator, a quote or a newline. */
    private String escape(String value) {
        if (value.indexOf(SEPARATOR) < 0
                && value.indexOf('"') < 0
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
