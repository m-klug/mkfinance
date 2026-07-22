package br.com.mkfinance.application.card;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Card;
import br.com.mkfinance.domain.model.CardPurchase;
import br.com.mkfinance.domain.model.Category;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.Installment;
import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.domain.service.InstallmentSplitter;
import br.com.mkfinance.domain.service.StatementAllocator;
import br.com.mkfinance.infrastructure.persistence.CardPurchaseRepository;
import br.com.mkfinance.infrastructure.persistence.CategoryRepository;
import br.com.mkfinance.infrastructure.persistence.InstallmentRepository;
import br.com.mkfinance.infrastructure.persistence.StatementRepository;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers what was charged to a card and spreads it across statements (R1, R3, R6).
 *
 * <p>Every installment of a purchase is created at once, allocated to consecutive statements from
 * the one the purchase date falls into. Re-editing a purchase re-runs that same allocation from
 * scratch instead of trying to patch individual installments: changing the amount, the date or the
 * number of installments can move rows between statements in ways that are fiddly to diff and easy
 * to get subtly wrong, whereas rebuilding is the same code path as creating and therefore obeys the
 * same rules by construction.
 *
 * <p>What rebuilding must not do is touch a bill already paid (R6) — so both the statements the
 * purchase currently occupies and the ones it would move to are checked for PAID before anything is
 * written, and the whole edit is refused as a unit rather than half-applied.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CardPurchaseService {

    private final CardPurchaseRepository purchaseRepository;
    private final InstallmentRepository installmentRepository;
    private final StatementRepository statementRepository;
    private final CategoryRepository categoryRepository;
    private final CardService cardService;
    private final StatementProvider statementProvider;
    private final StatementTotalsLoader totalsLoader;

    @Transactional(readOnly = true)
    public CardPurchase get(UUID workspaceId, UUID purchaseId) {
        return purchaseRepository
                .findByIdAndWorkspaceId(purchaseId, workspaceId)
                .orElseThrow(AppException::purchaseNotFound);
    }

    @Transactional(readOnly = true)
    public List<Installment> installmentsOf(CardPurchase purchase) {
        return installmentRepository.findByPurchaseIdOrderByNumberAsc(purchase.getId());
    }

    /** Reference month of each installment's statement, for rendering "2/3 — 2026-09". */
    @Transactional(readOnly = true)
    public Map<UUID, YearMonth> statementMonthsOf(List<Installment> installments) {
        Set<UUID> statementIds = installments.stream().map(Installment::getStatementId).collect(toSet());
        Map<UUID, YearMonth> months = new LinkedHashMap<>();
        statementRepository.findAllById(statementIds)
                .forEach(statement -> months.put(statement.getId(), statement.referenceYearMonth()));
        return months;
    }

    public CardPurchase create(UUID workspaceId, UUID cardId, CardPurchaseInput input) {
        Card card = cardService.getActive(workspaceId, cardId);
        Category category = requireExpenseCategory(workspaceId, input.categoryId());

        List<Statement> targets = allocate(card, input);
        requireAllMutable(targets);

        CardPurchase purchase = purchaseRepository.save(CardPurchase.builder()
                .workspaceId(workspaceId)
                .cardId(card.getId())
                .categoryId(category.getId())
                .description(input.description())
                .totalAmountCents(input.totalAmountCents())
                .purchaseDate(input.purchaseDate())
                .installmentCount(input.installmentCount())
                .build());

        writeInstallments(purchase, input, targets);
        refresh(targets);
        return purchase;
    }

    /**
     * Re-registers the purchase with new values (R6). The card never changes — the contract addresses
     * an existing purchase by id and carries no card — so only the allocation within that card moves.
     */
    public CardPurchase update(UUID workspaceId, UUID purchaseId, CardPurchaseInput input) {
        CardPurchase purchase = get(workspaceId, purchaseId);
        Card card = cardService.getActive(workspaceId, purchase.getCardId());
        Category category = requireExpenseCategory(workspaceId, input.categoryId());

        List<Statement> current = statementsOccupiedBy(purchase);
        List<Statement> targets = allocate(card, input);
        // Both ends of the move must be editable: leaving a paid bill is as forbidden as joining one.
        requireAllMutable(current);
        requireAllMutable(targets);

        purchase.setCategoryId(category.getId());
        purchase.setDescription(input.description());
        purchase.setTotalAmountCents(input.totalAmountCents());
        purchase.setPurchaseDate(input.purchaseDate());
        purchase.setInstallmentCount(input.installmentCount());
        purchaseRepository.save(purchase);

        installmentRepository.deleteByPurchaseId(purchase.getId());
        writeInstallments(purchase, input, targets);

        refresh(union(current, targets));
        return purchase;
    }

    public void delete(UUID workspaceId, UUID purchaseId) {
        CardPurchase purchase = get(workspaceId, purchaseId);
        List<Statement> current = statementsOccupiedBy(purchase);
        requireAllMutable(current);

        installmentRepository.deleteByPurchaseId(purchase.getId());
        purchaseRepository.delete(purchase);
        refresh(current);
    }

    /** The statements this purchase's installments would occupy, materializing them as needed (R3). */
    private List<Statement> allocate(Card card, CardPurchaseInput input) {
        List<YearMonth> months = StatementAllocator.referenceMonths(
                input.purchaseDate(), card.getClosingDay(), input.installmentCount());
        return months.stream().map(month -> statementProvider.statementOf(card, month)).toList();
    }

    private void writeInstallments(CardPurchase purchase, CardPurchaseInput input, List<Statement> targets) {
        long[] amounts = InstallmentSplitter.split(input.totalAmountCents(), input.installmentCount());
        List<Installment> installments = new ArrayList<>(amounts.length);
        for (int i = 0; i < amounts.length; i++) {
            installments.add(Installment.builder()
                    .purchaseId(purchase.getId())
                    .number((short) (i + 1))
                    .amountCents(amounts[i])
                    .statementId(targets.get(i).getId())
                    .build());
        }
        installmentRepository.saveAll(installments);
    }

    private List<Statement> statementsOccupiedBy(CardPurchase purchase) {
        Set<UUID> statementIds = installmentRepository.findByPurchaseIdOrderByNumberAsc(purchase.getId()).stream()
                .map(Installment::getStatementId)
                .collect(toSet());
        return statementRepository.findAllById(statementIds);
    }

    /** R6: a fully paid statement is frozen; corrections there go through an adjusting entry. */
    private void requireAllMutable(Collection<Statement> statements) {
        for (Statement statement : statements) {
            if (statement.chargesFrozen()) {
                throw AppException.statementImmutable(statement.referenceYearMonth().toString());
            }
        }
    }

    /** Totals changed, so the stored payment status of each touched statement may no longer hold. */
    private void refresh(Collection<Statement> statements) {
        statements.forEach(totalsLoader::refreshStatus);
    }

    private List<Statement> union(List<Statement> first, List<Statement> second) {
        Map<UUID, Statement> byId = new LinkedHashMap<>();
        first.forEach(statement -> byId.put(statement.getId(), statement));
        second.forEach(statement -> byId.put(statement.getId(), statement));
        return List.copyOf(byId.values());
    }

    private Category requireExpenseCategory(UUID workspaceId, UUID categoryId) {
        Category category = categoryRepository
                .findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(AppException::categoryNotFound);
        // A card purchase is spending: classifying it under an INCOME category would land it in no
        // pillar at all and quietly skew the allocation report (R10).
        if (category.getType() != CategoryType.EXPENSE) {
            throw AppException.categoryTypeMismatch();
        }
        return category;
    }

    private static <T> java.util.stream.Collector<T, ?, Set<T>> toSet() {
        return java.util.stream.Collectors.toCollection(LinkedHashSet::new);
    }
}
