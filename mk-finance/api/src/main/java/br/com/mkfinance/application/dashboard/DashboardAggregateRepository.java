package br.com.mkfinance.application.dashboard;

import br.com.mkfinance.domain.model.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The dashboard's aggregations, all done in the database (PLANEJAMENTO §6.6 acceptance: aggregate via
 * query, never by loading a workspace's transactions into memory to sum in Java). Every method is a
 * single {@code GROUP BY}/{@code SUM} scoped to one workspace and one month, so cost stays flat as
 * history grows.
 *
 * <p>These are read-only projections owned by BE-5. They live in this package rather than widening
 * the feature repositories of BE-3/BE-4, but they read those features' tables (R8 is preserved by the
 * mandatory {@code workspaceId} predicate on every query). Card purchases count on their
 * <em>purchase date</em> (R2 competency), not on when a statement was paid.
 *
 * <p>Every {@code Transaction}-backed query here (all but {@link #cardPurchasesByCategory}) also
 * filters on {@code status = SETTLED} (BE-6, R5): a scheduled/pending row is "projected", not
 * "realized", and recurrences (BE-7) generate pending rows by the dozen — without this predicate a
 * whole month of unconfirmed bills would already read as spent on day one. {@code card_purchase} has
 * no status column and is unaffected: a card purchase always counts on its purchase date (R2),
 * regardless of when — or whether — its statement gets paid.
 */
public interface DashboardAggregateRepository extends Repository<Transaction, UUID> {

    /**
     * The month's income (INCOME entries only — transfers are type TRANSFER and never counted, R4).
     *
     * <p>Joins {@code Category} and excludes {@code c.system = true} (BE-11, DESENVOLVIMENTO-CAIXINHAS.md
     * §4.5): a distributed investment yield is booked as INCOME under the system "Rendimento de
     * investimentos" category so the account balance and the extrato both reflect it, but the money
     * was not earned this month in the sense R10 measures pillars against — it stays out of this sum,
     * the same denominator {@code expenseTransactionsByCategory} already excludes system categories
     * from on the expense side (the statement-payment category, R2). This is the one query in this
     * class that doubles as both R10's denominator and the dashboard's "receitas do mês" tile
     * ({@code DashboardSummaryResponse.incomeCents}) — excluding system categories here keeps both
     * readings of "income" the same single definition, so they can never quietly disagree.
     */
    @Query(
            """
            select coalesce(sum(t.amountCents), 0)
            from Transaction t, Category c
            where t.categoryId = c.id
              and t.workspaceId = :workspaceId
              and t.type = br.com.mkfinance.domain.model.TransactionType.INCOME
              and t.status = br.com.mkfinance.domain.model.TransactionStatus.SETTLED
              and c.system = false
              and t.date between :from and :to
            """)
    long sumIncome(@Param("workspaceId") UUID workspaceId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * EXPENSE transactions of the month grouped by category, pillar and the system flag. The system
     * bucket is the statement-payment debit, which the allocation and category reports drop (R2).
     */
    @Query(
            """
            select new br.com.mkfinance.application.dashboard.CategoryPillarSpent(
                       t.categoryId, c.pillar, c.system, sum(t.amountCents))
            from Transaction t, Category c
            where t.categoryId = c.id
              and t.workspaceId = :workspaceId
              and t.type = br.com.mkfinance.domain.model.TransactionType.EXPENSE
              and t.status = br.com.mkfinance.domain.model.TransactionStatus.SETTLED
              and t.date between :from and :to
            group by t.categoryId, c.pillar, c.system
            """)
    List<CategoryPillarSpent> expenseTransactionsByCategory(
            @Param("workspaceId") UUID workspaceId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Card purchases of the month grouped by category and pillar, counted on their purchase date (R2
     * competency) — never on the date the statement was paid. Card purchases never carry a system
     * category.
     */
    @Query(
            """
            select new br.com.mkfinance.application.dashboard.CategoryPillarAmount(
                       cp.categoryId, c.pillar, sum(cp.totalAmountCents))
            from CardPurchase cp, Category c
            where cp.categoryId = c.id
              and cp.workspaceId = :workspaceId
              and cp.purchaseDate between :from and :to
            group by cp.categoryId, c.pillar
            """)
    List<CategoryPillarAmount> cardPurchasesByCategory(
            @Param("workspaceId") UUID workspaceId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * The month's contributions (R9), grouped by pillar. Reads only the IN leg carrying
     * {@code contribution_pillar}, so each aporte is summed exactly once.
     */
    @Query(
            """
            select new br.com.mkfinance.application.dashboard.PillarContribution(
                       t.contributionPillar, sum(t.amountCents))
            from Transaction t
            where t.workspaceId = :workspaceId
              and t.type = br.com.mkfinance.domain.model.TransactionType.TRANSFER
              and t.transferDirection = br.com.mkfinance.domain.model.TransferDirection.IN
              and t.contributionPillar is not null
              and t.status = br.com.mkfinance.domain.model.TransactionStatus.SETTLED
              and t.date between :from and :to
            group by t.contributionPillar
            """)
    List<PillarContribution> contributionsByPillar(
            @Param("workspaceId") UUID workspaceId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
