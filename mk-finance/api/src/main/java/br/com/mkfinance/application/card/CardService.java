package br.com.mkfinance.application.card;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Card;
import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.domain.model.StatementStatus;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.CardRepository;
import br.com.mkfinance.infrastructure.persistence.StatementRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Card CRUD plus the available-limit calculation the card list is built on. */
@Service
@RequiredArgsConstructor
@Transactional
public class CardService {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final StatementRepository statementRepository;
    private final StatementTotalsLoader totalsLoader;

    @Transactional(readOnly = true)
    public List<Card> list(UUID workspaceId, boolean includeArchived) {
        return includeArchived
                ? cardRepository.findByWorkspaceIdOrderByNameAsc(workspaceId)
                : cardRepository.findByWorkspaceIdAndArchivedFalseOrderByNameAsc(workspaceId);
    }

    @Transactional(readOnly = true)
    public Card get(UUID workspaceId, UUID cardId) {
        return cardRepository.findByIdAndWorkspaceId(cardId, workspaceId).orElseThrow(AppException::cardNotFound);
    }

    /** A card that still accepts charges — archived ones are read-only history (R6 in spirit). */
    @Transactional(readOnly = true)
    public Card getActive(UUID workspaceId, UUID cardId) {
        Card card = get(workspaceId, cardId);
        if (card.isArchived()) {
            throw AppException.cardArchived();
        }
        return card;
    }

    public Card create(UUID workspaceId, CardInput input) {
        requireOwnedPaymentAccount(workspaceId, input.defaultPaymentAccountId());
        Card card = Card.builder()
                .workspaceId(workspaceId)
                .name(input.name())
                .brand(input.brand())
                .limitCents(input.limitCents())
                .closingDay(input.closingDay())
                .dueDay(input.dueDay())
                .defaultPaymentAccountId(input.defaultPaymentAccountId())
                .color(input.color())
                .build();
        return cardRepository.save(card);
    }

    /**
     * Updates the card. Changing {@code closingDay}/{@code dueDay} deliberately does not rewrite
     * statements that already exist: their dates were frozen when they were materialized, and a bill
     * that already closed on the 20th did close on the 20th (contract).
     */
    public Card update(UUID workspaceId, UUID cardId, CardInput input) {
        Card card = get(workspaceId, cardId);
        requireOwnedPaymentAccount(workspaceId, input.defaultPaymentAccountId());

        card.setName(input.name());
        card.setBrand(input.brand());
        card.setLimitCents(input.limitCents());
        card.setClosingDay(input.closingDay());
        card.setDueDay(input.dueDay());
        card.setDefaultPaymentAccountId(input.defaultPaymentAccountId());
        card.setColor(input.color());
        return cardRepository.save(card);
    }

    /**
     * Archives the card — idempotent, exactly like {@code AccountService.archive}.
     *
     * <p>Archiving is the counterpart of deletion for something with history: the card keeps its
     * purchases, installments and statements, stays reachable through
     * {@code listCards?includeArchived=true}, and its open bills can still be paid — only new
     * charges are refused, by {@link #getActive} answering CARD_ARCHIVED.
     */
    public Card archive(UUID workspaceId, UUID cardId) {
        Card card = get(workspaceId, cardId);
        card.setArchived(true);
        return cardRepository.save(card);
    }

    /**
     * Available limit = limit − everything still owed on statements that are not fully paid.
     *
     * <p>"Still owed" spans <em>future</em> statements too, because installments are all created
     * up-front: a 12× purchase commits the whole amount the moment it is made, and a limit that
     * ignored the eleven months ahead would tell the user they can spend money they have already
     * spent. Partial payments count, so paying half a bill frees half of it.
     *
     * <p>Can go negative when the card is over its limit — that is real information, not an error.
     */
    @Transactional(readOnly = true)
    public Map<UUID, Long> availableLimitByCard(List<Card> cards) {
        if (cards.isEmpty()) {
            return Map.of();
        }
        List<UUID> cardIds = cards.stream().map(Card::getId).toList();
        List<Statement> statements = statementRepository.findByCardIdIn(cardIds);
        Map<UUID, StatementTotals> totals = totalsLoader.loadFor(statements);

        Map<UUID, Long> outstandingByCard = new HashMap<>();
        for (Statement statement : statements) {
            if (statement.getStatus() == StatementStatus.PAID) {
                continue;
            }
            long remaining = totals.getOrDefault(statement.getId(), StatementTotals.EMPTY).remainingCents();
            outstandingByCard.merge(statement.getCardId(), remaining, Long::sum);
        }

        Map<UUID, Long> available = new HashMap<>(cards.size());
        for (Card card : cards) {
            available.put(card.getId(), card.getLimitCents() - outstandingByCard.getOrDefault(card.getId(), 0L));
        }
        return available;
    }

    private void requireOwnedPaymentAccount(UUID workspaceId, UUID accountId) {
        if (accountId == null) {
            return;
        }
        accountRepository
                .findByIdAndWorkspaceId(accountId, workspaceId)
                .orElseThrow(AppException::accountNotFound);
    }
}
