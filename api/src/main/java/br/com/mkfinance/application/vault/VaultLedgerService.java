package br.com.mkfinance.application.vault;

import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.VaultEntry;
import br.com.mkfinance.domain.model.VaultEntrySource;
import br.com.mkfinance.domain.service.BalanceCalculator;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import br.com.mkfinance.infrastructure.persistence.VaultEntryRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A vault's own ledger, "the extrato of the caixinha" (contract {@code listVaultEntries}).
 *
 * <p>Not a database aggregate like {@code VaultBalanceService} — a vault's history is bounded by
 * what a person can contribute toward one goal, not by workspace-wide volume, so batching the
 * (small) transaction lookup and sorting/filtering in memory is the right cost here, the same way
 * {@code TransactionService} resolves tags in a batch rather than per-row.
 */
@Service
@RequiredArgsConstructor
public class VaultLedgerService {

    private final VaultEntryRepository vaultEntryRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Most recent first (contract). {@code from}/{@code to} are optional and compared against each
     * row's resolved date — the transaction's date for CONTRIBUTION/WITHDRAWAL/YIELD, the
     * reallocation's own date otherwise.
     */
    @Transactional(readOnly = true)
    public List<VaultLedgerLine> listEntries(UUID vaultId, LocalDate from, LocalDate to) {
        List<VaultEntry> entries = vaultEntryRepository.findByVaultId(vaultId);
        if (entries.isEmpty()) {
            return List.of();
        }

        Set<UUID> transactionIds = new HashSet<>();
        for (VaultEntry entry : entries) {
            if (entry.getTransactionId() != null) {
                transactionIds.add(entry.getTransactionId());
            }
        }
        Map<UUID, Transaction> transactionsById = transactionRepository.findAllById(transactionIds).stream()
                .collect(Collectors.toMap(Transaction::getId, transaction -> transaction));

        List<VaultLedgerLine> lines = new ArrayList<>(entries.size());
        for (VaultEntry entry : entries) {
            VaultLedgerLine line = resolve(entry, transactionsById);
            if (from != null && line.date().isBefore(from)) {
                continue;
            }
            if (to != null && line.date().isAfter(to)) {
                continue;
            }
            lines.add(line);
        }
        lines.sort(Comparator.comparing(VaultLedgerLine::date)
                .thenComparing(VaultLedgerLine::createdAt)
                .reversed());
        return lines;
    }

    private VaultLedgerLine resolve(VaultEntry entry, Map<UUID, Transaction> transactionsById) {
        if (entry.getSource() == VaultEntrySource.REALLOCATION) {
            return new VaultLedgerLine(
                    entry.getId(),
                    entry.getVaultId(),
                    entry.getDate(),
                    entry.getDescription(),
                    entry.getAmountCents(),
                    entry.getSource(),
                    TransactionStatus.SETTLED,
                    null,
                    entry.getReallocationId(),
                    entry.getCreatedAt());
        }

        Transaction transaction = transactionsById.get(entry.getTransactionId());
        if (transaction == null) {
            // The FK guarantees this cannot happen outside corrupted data.
            throw new IllegalStateException(
                    "VaultEntry " + entry.getId() + " points at a missing transaction " + entry.getTransactionId());
        }
        long amountCents = entry.getSource() == VaultEntrySource.YIELD
                ? entry.getAmountCents() // already signed on the ledger
                : BalanceCalculator.signedAmountCents(new BalanceCalculator.Entry(
                        transaction.getType(),
                        transaction.getStatus(),
                        transaction.getTransferDirection(),
                        transaction.getAmountCents())); // §4.1 — reused, never reimplemented
        return new VaultLedgerLine(
                entry.getId(),
                entry.getVaultId(),
                transaction.getDate(),
                transaction.getDescription(),
                amountCents,
                entry.getSource(),
                transaction.getStatus(),
                transaction.getId(),
                entry.getReallocationId(),
                entry.getCreatedAt());
    }
}
