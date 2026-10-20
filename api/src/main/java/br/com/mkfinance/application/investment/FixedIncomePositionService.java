package br.com.mkfinance.application.investment;

import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.marketrate.IndexHistoryService;
import br.com.mkfinance.application.transaction.TransactionService;
import br.com.mkfinance.application.vault.VaultYieldInput;
import br.com.mkfinance.application.vault.VaultYieldResult;
import br.com.mkfinance.application.vault.VaultYieldService;
import br.com.mkfinance.application.marketrate.MarketRateService;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.FixedIncomePosition;
import br.com.mkfinance.domain.model.IndexCode;
import br.com.mkfinance.domain.model.MarketRate;
import br.com.mkfinance.domain.model.MarketRateCode;
import br.com.mkfinance.domain.model.PositionStatus;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransferDirection;
import br.com.mkfinance.domain.model.Vault;
import br.com.mkfinance.domain.model.VaultEntry;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomePositionValuator;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomePositionValuator.Estimate;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomePositionValuator.PositionTerms;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.FixedIncomePositionRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import br.com.mkfinance.infrastructure.persistence.VaultEntryRepository;
import br.com.mkfinance.infrastructure.persistence.VaultRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixed-income positions (DESENVOLVIMENTO-POSICOES.md): a title with a rate and a maturity that
 * details the balance of an investment account. A position never moves money, so nothing here
 * touches a balance or a pillar; it only reads purchases (transfers) and vaults, and values the
 * title through {@link FixedIncomePositionValuator}.
 *
 * <p>Deliberately not {@code readOnly}: valuing a post-fixed title may extend the stored daily-index
 * history, which writes rows, and a read-only transaction would silently never flush them.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FixedIncomePositionService {

    private final FixedIncomePositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final VaultRepository vaultRepository;
    private final VaultEntryRepository vaultEntryRepository;
    private final VaultYieldService vaultYieldService;
    private final TransactionService transactionService;
    private final IndexHistoryService indexHistory;
    private final MarketRateService marketRates;
    private final BusinessClock clock;

    public PositionView create(UUID workspaceId, FixedIncomePositionInput input) {
        boolean linked = input.contributionTransactionId() != null;
        boolean manualFields = input.accountId() != null || input.principalCents() != null || input.purchaseDate() != null;
        if (linked == manualFields) {
            throw AppException.positionOriginInvalid();
        }

        FixedIncomePosition position = linked ? fromContribution(workspaceId, input) : fromManual(workspaceId, input);
        position.setName(blankToNull(input.name()));
        position.setType(input.type());
        position.setIndexer(input.indexer());
        position.setRate(input.rate());
        position.setMaturityDate(input.maturityDate());
        validateTerms(position);

        return views(List.of(positionRepository.save(position))).get(0);
    }

    public List<PositionView> list(UUID workspaceId, PositionStatus status) {
        List<FixedIncomePosition> positions = status == null
                ? positionRepository.findByWorkspaceIdOrderByMaturityDateAscCreatedAtAsc(workspaceId)
                : positionRepository.findByWorkspaceIdAndStatusOrderByMaturityDateAscCreatedAtAsc(workspaceId, status);
        return views(positions);
    }

    public PositionView get(UUID workspaceId, UUID positionId) {
        return views(List.of(owned(workspaceId, positionId))).get(0);
    }

    public PositionView update(UUID workspaceId, UUID positionId, FixedIncomePositionUpdate update) {
        FixedIncomePosition position = owned(workspaceId, positionId);
        if (position.isSettled()) {
            throw AppException.positionSettled();
        }
        position.setType(update.type());
        position.setIndexer(update.indexer());
        position.setRate(update.rate());
        position.setMaturityDate(update.maturityDate());
        position.setName(blankToNull(update.name()));
        position.setVaultId(update.vaultId() == null ? null : requireVault(workspaceId, position.getAccountId(), update.vaultId()).getId());
        if (position.isManual()) {
            if (update.principalCents() != null) {
                position.setPrincipalCents(update.principalCents());
            }
            if (update.purchaseDate() != null) {
                position.setPurchaseDate(update.purchaseDate());
            }
        }
        validateTerms(position);
        return views(List.of(positionRepository.save(position))).get(0);
    }

    public void delete(UUID workspaceId, UUID positionId) {
        FixedIncomePosition position = owned(workspaceId, positionId);
        if (position.isSettled()) {
            throw AppException.positionSettled();
        }
        positionRepository.delete(position);
    }

    // ------------------------------------------------------------------ payment

    /**
     * Books the payment of a title: the real yield (received minus principal) goes to the position's
     * vault, or stays unallocated when it has none, and the position remembers what was received next
     * to what the app had estimated. The money that fell in the checking account is NOT recorded
     * here — that transfer keeps coming from the bank statement import or from a manual entry, and may
     * be linked with {@code returnTransferTransactionId}. The yield is booked first on purpose: the
     * transfer back takes principal and yield out of the vault, which only has them once the yield is in.
     *
     * @throws AppException POSITION_SETTLED when already paid, POSITION_DATES_INVALID for a date
     *     before the purchase or in the future, POSITION_RECEIVED_BELOW_PRINCIPAL,
     *     POSITION_RETURN_TRANSFER_INVALID, POSITION_VAULT_INVALID when the position's vault was archived
     */
    public PositionView settle(UUID workspaceId, UUID positionId, PositionSettlementInput input) {
        FixedIncomePosition position = owned(workspaceId, positionId);
        if (position.isSettled()) {
            throw AppException.positionSettled();
        }
        if (input.settledDate().isBefore(position.getPurchaseDate()) || input.settledDate().isAfter(clock.today())) {
            throw AppException.positionDatesInvalid();
        }
        if (input.receivedCents() < position.getPrincipalCents()) {
            throw AppException.positionReceivedBelowPrincipal(position.getPrincipalCents());
        }
        UUID returnTransfer = input.returnTransferTransactionId() == null
                ? null
                : requireReturnTransfer(workspaceId, position, input.returnTransferTransactionId());

        long yield = input.receivedCents() - position.getPrincipalCents();
        UUID yieldTransaction = null;
        if (yield > 0) {
            if (position.getVaultId() != null) {
                requireVault(workspaceId, position.getAccountId(), position.getVaultId());
            }
            VaultYieldResult booked = vaultYieldService.createForVault(
                    workspaceId,
                    position.getAccountId(),
                    new VaultYieldInput(yield, input.settledDate(), yieldDescription(position)),
                    position.getVaultId());
            yieldTransaction = booked.transactionId();
        }

        position.setStatus(PositionStatus.SETTLED);
        position.setSettledDate(input.settledDate());
        position.setReceivedCents(input.receivedCents());
        position.setEstimatedNetCents(estimatedNetOn(position, input.settledDate()));
        position.setYieldCents(yield);
        position.setYieldTransactionId(yieldTransaction);
        position.setReturnTransferTransactionId(returnTransfer);
        return views(List.of(positionRepository.save(position))).get(0);
    }

    /**
     * Undoes a payment: removes the booked yield (and its split between vaults with it), forgets what
     * was received and the link to the transfer back, and returns the position to active — so a wrong
     * amount is fixed by undoing and paying again. The position stops referencing the yield first,
     * because while it does the yield cannot be deleted.
     */
    public PositionView unsettle(UUID workspaceId, UUID positionId) {
        FixedIncomePosition position = owned(workspaceId, positionId);
        if (!position.isSettled()) {
            throw AppException.positionNotSettled();
        }
        UUID yieldTransaction = position.getYieldTransactionId();

        position.setStatus(PositionStatus.ACTIVE);
        position.setSettledDate(null);
        position.setReceivedCents(null);
        position.setEstimatedNetCents(null);
        position.setYieldCents(null);
        position.setYieldTransactionId(null);
        position.setReturnTransferTransactionId(null);
        positionRepository.saveAndFlush(position);

        if (yieldTransaction != null) {
            transactionService.delete(workspaceId, yieldTransaction);
        }
        return views(List.of(position)).get(0);
    }

    /** Links (or, with null, unlinks) the transfer back to checking after the title was paid. */
    public PositionView linkReturnTransfer(UUID workspaceId, UUID positionId, UUID returnTransferTransactionId) {
        FixedIncomePosition position = owned(workspaceId, positionId);
        if (!position.isSettled()) {
            throw AppException.positionNotSettled();
        }
        position.setReturnTransferTransactionId(returnTransferTransactionId == null
                ? null
                : requireReturnTransfer(workspaceId, position, returnTransferTransactionId));
        return views(List.of(positionRepository.save(position))).get(0);
    }

    /** An outgoing transfer leg of the position's own account that no other position claims. */
    private UUID requireReturnTransfer(UUID workspaceId, FixedIncomePosition position, UUID transactionId) {
        Transaction leg = transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)
                .orElseThrow(AppException::positionReturnTransferInvalid);
        boolean claimedByAnother = positionRepository.existsReferencing(leg.getId())
                && !leg.getId().equals(position.getReturnTransferTransactionId());
        if (!leg.isTransfer()
                || leg.getTransferDirection() != TransferDirection.OUT
                || !leg.getAccountId().equals(position.getAccountId())
                || claimedByAnother) {
            throw AppException.positionReturnTransferInvalid();
        }
        return leg.getId();
    }

    /** What the app estimated the title would net on {@code date}; null when it cannot be computed. */
    private Long estimatedNetOn(FixedIncomePosition position, LocalDate date) {
        LocalDate today = clock.today();
        try {
            NavigableMap<LocalDate, BigDecimal> daily = new TreeMap<>();
            BigDecimal future = null;
            if (position.getIndexer() != Indexer.PREFIXED) {
                IndexCode code = position.getIndexer() == Indexer.CDI_PERCENT ? IndexCode.CDI : IndexCode.SELIC;
                daily = indexHistory.dailyPercents(code, position.getPurchaseDate(), today);
                future = currentRates().get(code == IndexCode.CDI ? MarketRateCode.CDI : MarketRateCode.SELIC);
            }
            return FixedIncomePositionValuator.valueOn(termsOf(position), date, today, daily, future).netCents();
        } catch (IllegalArgumentException cannotBeValued) {
            return null;
        }
    }

    private static String yieldDescription(FixedIncomePosition position) {
        return "Rendimento " + (position.getName() != null ? position.getName() : position.getType().name());
    }

    /** Purchases a position can still be born from, newest first. */
    public List<UnlinkedContribution> unlinkedContributions(UUID workspaceId) {
        List<Transaction> legs = positionRepository.findUnlinkedContributions(workspaceId);
        if (legs.isEmpty()) {
            return List.of();
        }
        Map<UUID, UUID> vaultByLeg = vaultEntryRepository
                .findByTransactionIdInAndAmountCentsIsNull(legs.stream().map(Transaction::getId).toList()).stream()
                .collect(Collectors.toMap(VaultEntry::getTransactionId, VaultEntry::getVaultId, (a, b) -> a));
        return legs.stream().map(leg -> new UnlinkedContribution(leg, vaultByLeg.get(leg.getId()))).toList();
    }

    // ------------------------------------------------------------------ creation

    private FixedIncomePosition fromContribution(UUID workspaceId, FixedIncomePositionInput input) {
        Transaction leg = transactionRepository
                .findByIdAndWorkspaceId(input.contributionTransactionId(), workspaceId)
                .orElseThrow(AppException::positionContributionInvalid);
        if (!leg.isTransfer()
                || leg.getTransferDirection() != TransferDirection.IN
                || leg.getStatus() != TransactionStatus.SETTLED) {
            throw AppException.positionContributionInvalid();
        }
        Account account = accountRepository.findByIdAndWorkspaceId(leg.getAccountId(), workspaceId)
                .orElseThrow(AppException::positionContributionInvalid);
        if (!holdsInvestments(account)) {
            throw AppException.positionContributionInvalid();
        }
        if (positionRepository.existsByContributionTransactionId(leg.getId())) {
            throw AppException.positionAlreadyExists();
        }

        UUID transferVault = vaultEntryRepository
                .findByTransactionIdInAndAmountCentsIsNull(List.of(leg.getId())).stream()
                .findFirst().map(VaultEntry::getVaultId).orElse(null);
        UUID vaultId = input.vaultId() != null
                ? requireVault(workspaceId, account.getId(), input.vaultId()).getId()
                : transferVault;

        return FixedIncomePosition.builder()
                .workspaceId(workspaceId)
                .accountId(account.getId())
                .vaultId(vaultId)
                .contributionTransactionId(leg.getId())
                .principalCents(leg.getAmountCents())
                .purchaseDate(leg.getDate())
                .build();
    }

    private FixedIncomePosition fromManual(UUID workspaceId, FixedIncomePositionInput input) {
        if (input.accountId() == null || input.principalCents() == null || input.purchaseDate() == null) {
            throw AppException.positionOriginInvalid();
        }
        Account account = accountRepository.findByIdAndWorkspaceId(input.accountId(), workspaceId)
                .orElseThrow(AppException::positionAccountInvalid);
        if (!holdsInvestments(account) || account.isArchived()) {
            throw AppException.positionAccountInvalid();
        }
        UUID vaultId = input.vaultId() == null ? null : requireVault(workspaceId, account.getId(), input.vaultId()).getId();

        return FixedIncomePosition.builder()
                .workspaceId(workspaceId)
                .accountId(account.getId())
                .vaultId(vaultId)
                .principalCents(input.principalCents())
                .purchaseDate(input.purchaseDate())
                .build();
    }

    // ------------------------------------------------------------------ rules

    private void validateTerms(FixedIncomePosition position) {
        InstrumentType type = position.getType();
        Indexer indexer = position.getIndexer();
        if (indexer == Indexer.IPCA_PLUS || !FixedIncomeParams.indexerFits(type, indexer)) {
            throw AppException.positionIndexerNotAvailable(type.name(), indexer.name());
        }
        if (position.getPrincipalCents() <= 0
                || !position.getMaturityDate().isAfter(position.getPurchaseDate())
                || position.getPurchaseDate().isAfter(clock.today())) {
            throw AppException.positionDatesInvalid();
        }
    }

    private static boolean holdsInvestments(Account account) {
        return account.getType() == AccountType.INVESTMENT || account.getType() == AccountType.SAVINGS;
    }

    private Vault requireVault(UUID workspaceId, UUID accountId, UUID vaultId) {
        Vault vault = vaultRepository.findByIdAndWorkspaceId(vaultId, workspaceId)
                .orElseThrow(AppException::positionVaultInvalid);
        if (!vault.getAccountId().equals(accountId) || vault.isArchived()) {
            throw AppException.positionVaultInvalid();
        }
        return vault;
    }

    private FixedIncomePosition owned(UUID workspaceId, UUID positionId) {
        return positionRepository.findByIdAndWorkspaceId(positionId, workspaceId)
                .orElseThrow(AppException::positionNotFound);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ------------------------------------------------------------------ estimates

    /**
     * Pairs each position with its estimate. The stored daily index is read once per index (from the
     * earliest purchase among the positions that follow it), and the current rates only when some
     * post-fixed active position needs them.
     */
    private List<PositionView> views(List<FixedIncomePosition> positions) {
        LocalDate today = clock.today();
        Map<IndexCode, NavigableMap<LocalDate, BigDecimal>> series = new EnumMap<>(IndexCode.class);
        Map<MarketRateCode, BigDecimal> current = null;

        List<PositionView> views = new ArrayList<>(positions.size());
        for (FixedIncomePosition position : positions) {
            if (position.isSettled() || position.getIndexer() == Indexer.IPCA_PLUS) {
                views.add(new PositionView(position, null));
                continue;
            }
            Estimate estimate = null;
            try {
                NavigableMap<LocalDate, BigDecimal> daily = new TreeMap<>();
                BigDecimal future = null;
                if (position.getIndexer() != Indexer.PREFIXED) {
                    IndexCode code = position.getIndexer() == Indexer.CDI_PERCENT ? IndexCode.CDI : IndexCode.SELIC;
                    if (!series.containsKey(code)) {
                        LocalDate earliest = positions.stream()
                                .filter(p -> !p.isSettled() && sameIndex(p, code))
                                .map(FixedIncomePosition::getPurchaseDate)
                                .min(LocalDate::compareTo)
                                .orElse(position.getPurchaseDate());
                        series.put(code, indexHistory.dailyPercents(code, earliest, today));
                    }
                    daily = series.get(code);
                    if (current == null) {
                        current = currentRates();
                    }
                    future = current.get(code == IndexCode.CDI ? MarketRateCode.CDI : MarketRateCode.SELIC);
                }
                estimate = FixedIncomePositionValuator.estimate(termsOf(position), today, daily, future);
            } catch (IllegalArgumentException cannotBeValued) {
                // No history and no current rate to project with: the position is shown without an estimate.
            }
            views.add(new PositionView(position, estimate));
        }
        return views;
    }

    private static boolean sameIndex(FixedIncomePosition position, IndexCode code) {
        return (code == IndexCode.CDI && position.getIndexer() == Indexer.CDI_PERCENT)
                || (code == IndexCode.SELIC && position.getIndexer() == Indexer.SELIC_PERCENT);
    }

    private Map<MarketRateCode, BigDecimal> currentRates() {
        return marketRates.current().stream()
                .collect(Collectors.toMap(MarketRate::getCode, MarketRate::getRatePercent, (a, b) -> a,
                        () -> new EnumMap<>(MarketRateCode.class)));
    }

    private static PositionTerms termsOf(FixedIncomePosition position) {
        return new PositionTerms(position.getType(), position.getIndexer(), position.getRate(),
                position.getPrincipalCents(), position.getPurchaseDate(), position.getMaturityDate());
    }
}
