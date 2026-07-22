package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.service.BalanceCalculator;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.domain.service.BalanceCalculator.Entry;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionBalanceBucket;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Current and projected balances per account (PLANEJAMENTO §5 R5), owned by BE-3.
 *
 * <p>The arithmetic itself lives in {@link BalanceCalculator}; this class only feeds it. It reads
 * pre-aggregated {@code group by (account, type, status, direction)} buckets, so computing the
 * balance of every account in a workspace is a single query returning a handful of rows — the
 * numbers stay correct after ten years of history without the cost growing.
 *
 * <p>Available to any workstream that needs balances (the accounts endpoint of BE-2, the dashboard
 * of BE-5) — nobody should re-derive this arithmetic.
 */
@Service
@RequiredArgsConstructor
public class AccountBalanceService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    /** Balance of every account in the workspace, keyed by account id — accounts with no history included. */
    @Transactional(readOnly = true)
    public Map<UUID, Balance> balancesByAccount(UUID workspaceId) {
        Map<UUID, List<Entry>> entriesByAccount = new HashMap<>();
        for (TransactionBalanceBucket bucket : transactionRepository.aggregateBalanceBuckets(workspaceId)) {
            entriesByAccount
                    .computeIfAbsent(bucket.accountId(), key -> new ArrayList<>())
                    .add(new Entry(bucket.type(), bucket.status(), bucket.direction(), bucket.amountCents()));
        }

        Map<UUID, Balance> balances = new HashMap<>();
        for (Account account : accountRepository.findByWorkspaceIdOrderByNameAsc(workspaceId)) {
            List<Entry> entries = entriesByAccount.getOrDefault(account.getId(), List.of());
            balances.put(account.getId(), BalanceCalculator.balance(account.getInitialBalanceCents(), entries));
        }
        return balances;
    }

    /** Balance of a single account; an account with no transactions sits at its initial balance. */
    @Transactional(readOnly = true)
    public Balance balanceOf(UUID workspaceId, UUID accountId) {
        Account account = TransactionLookup.ownedAccount(accountRepository, workspaceId, accountId);
        Balance balance = balancesByAccount(workspaceId).get(accountId);
        return balance != null ? balance : Balance.ofInitial(account.getInitialBalanceCents());
    }
}
