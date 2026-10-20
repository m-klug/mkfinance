package br.com.mkfinance.application.investment;

import br.com.mkfinance.application.dashboard.DashboardService;
import br.com.mkfinance.application.investment.InvestmentSummary.AssetClass;
import br.com.mkfinance.application.investment.InvestmentSummary.AssetClassCode;
import br.com.mkfinance.application.investment.InvestmentSummary.InvestmentAccount;
import br.com.mkfinance.application.transaction.AccountBalanceService;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.FixedIncomePosition;
import br.com.mkfinance.domain.model.PositionStatus;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The net-worth view: every account's balance, plus the yield the active fixed-income positions have
 * accrued and not yet received. Reads only; the numbers are never stored.
 *
 * <p>Only active positions on non-archived accounts count: a paid position's yield is already in its
 * account's balance, and an archived account's balance is outside the total. Investment and savings
 * accounts are grouped together (R9 treats them alike as contribution targets); every other account
 * is "accounts and cash".
 */
@Service
@RequiredArgsConstructor
public class InvestmentSummaryService {

    private final AccountRepository accountRepository;
    private final AccountBalanceService accountBalanceService;
    private final FixedIncomePositionService positionService;
    private final DashboardService dashboardService;

    public InvestmentSummary summary(UUID workspaceId) {
        Map<UUID, Balance> balances = accountBalanceService.balancesByAccount(workspaceId);
        List<Account> accounts = accountRepository.findByWorkspaceIdAndArchivedFalseOrderByNameAsc(workspaceId);

        long accountsAndCash = 0;
        List<Account> investmentAccounts = new ArrayList<>();
        for (Account account : accounts) {
            if (holdsInvestments(account)) {
                investmentAccounts.add(account);
            } else {
                accountsAndCash += balanceOf(balances, account);
            }
        }

        Map<UUID, Totals> byAccount = new HashMap<>();
        for (PositionView view : positionService.list(workspaceId, PositionStatus.ACTIVE)) {
            FixedIncomePosition position = view.position();
            byAccount.computeIfAbsent(position.getAccountId(), id -> new Totals()).add(position, view);
        }

        long investmentBalance = 0;
        long unregistered = 0;
        boolean exceeds = false;
        long applied = 0;
        long estimatedValue = 0;
        int count = 0;
        int withoutEstimate = 0;
        List<InvestmentAccount> accountViews = new ArrayList<>();
        for (Account account : investmentAccounts) {
            long balance = balanceOf(balances, account);
            Totals totals = byAccount.getOrDefault(account.getId(), new Totals());
            boolean accountExceeds = totals.principal > balance;
            long accountUnregistered = Math.max(0, balance - totals.principal);

            accountViews.add(new InvestmentAccount(
                    account.getId(), account.getName(), balance, totals.principal,
                    totals.value - totals.principal, accountUnregistered, accountExceeds));
            investmentBalance += balance;
            unregistered += accountUnregistered;
            exceeds |= accountExceeds;
            applied += totals.principal;
            estimatedValue += totals.value;
            count += totals.positions;
            withoutEstimate += totals.withoutEstimate;
        }

        long accrued = estimatedValue - applied;
        long investments = investmentBalance + accrued;
        return new InvestmentSummary(
                accountsAndCash,
                investments,
                accountsAndCash + investments,
                dashboardService.openStatementsRemainingCents(workspaceId),
                List.of(new AssetClass(AssetClassCode.FIXED_INCOME, applied, estimatedValue, accrued, count, withoutEstimate)),
                accountViews,
                unregistered,
                exceeds);
    }

    private static boolean holdsInvestments(Account account) {
        return account.getType() == AccountType.INVESTMENT || account.getType() == AccountType.SAVINGS;
    }

    private static long balanceOf(Map<UUID, Balance> balances, Account account) {
        return balances.getOrDefault(account.getId(), Balance.ofInitial(account.getInitialBalanceCents())).currentCents();
    }

    /** The active positions of one account, summed. */
    private static final class Totals {
        long principal;
        long value;
        int positions;
        int withoutEstimate;

        void add(FixedIncomePosition position, PositionView view) {
            principal += position.getPrincipalCents();
            positions++;
            if (view.estimate() == null) {
                value += position.getPrincipalCents(); // valued at cost when the app cannot estimate it
                withoutEstimate++;
            } else {
                value += view.estimate().today().netCents();
            }
        }
    }
}
