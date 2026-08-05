import { http, HttpResponse } from "msw";

import {
  AccountType,
  CategoryType,
  ContributionPillar,
  Pillar,
  StatementStatus,
  TransactionStatus,
  TransactionType,
  type Account,
  type Card,
  type Category,
  type Statement,
  type StatementDetail,
  type Tag,
  type Transaction,
  type TransactionPage,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

// The orval-generated MSW mocks answer with random faker data and a 1s delay,
// which is right for eyeballing the UI but useless for assertions. These
// fixtures + handlers give the feature tests a small, stable workspace.

export const checkingAccount: Account = {
  id: "acc-checking",
  name: "Conta Corrente",
  type: AccountType.CHECKING,
  initialBalanceCents: 100000,
  balanceCents: 250000,
  projectedBalanceCents: 230000,
  defaultContributionPillar: null,
  archived: false,
};

export const investmentAccount: Account = {
  id: "acc-investment",
  name: "Corretora",
  type: AccountType.INVESTMENT,
  initialBalanceCents: 0,
  balanceCents: 500000,
  projectedBalanceCents: 500000,
  // R9: contributions into this account consume the Goals pillar by default.
  defaultContributionPillar: ContributionPillar.GOALS,
  archived: false,
};

export const archivedAccount: Account = {
  id: "acc-archived",
  name: "Conta Antiga",
  type: AccountType.CHECKING,
  initialBalanceCents: 0,
  balanceCents: 0,
  projectedBalanceCents: 0,
  defaultContributionPillar: null,
  archived: true,
};

export const groceriesCategory: Category = {
  id: "cat-groceries",
  name: "Mercado",
  type: CategoryType.EXPENSE,
  pillar: Pillar.FIXED_COSTS,
  parentId: null,
  system: false,
};

export const streamingCategory: Category = {
  id: "cat-streaming",
  name: "Streaming",
  type: CategoryType.EXPENSE,
  pillar: Pillar.PLEASURES,
  parentId: null,
  system: false,
};

export const salaryCategory: Category = {
  id: "cat-salary",
  name: "Salário",
  type: CategoryType.INCOME,
  pillar: null,
  parentId: null,
  system: false,
};

export const statementPaymentCategory: Category = {
  id: "cat-statement-payment",
  name: "Pagamento de fatura",
  type: CategoryType.EXPENSE,
  pillar: Pillar.FIXED_COSTS,
  parentId: null,
  system: true,
};

export const settledExpense: Transaction = {
  id: "tx-settled",
  accountId: checkingAccount.id,
  categoryId: groceriesCategory.id,
  type: TransactionType.EXPENSE,
  amountCents: 15990,
  date: "2026-07-10",
  description: "Compra do mês",
  status: TransactionStatus.SETTLED,
};

export const pendingExpense: Transaction = {
  id: "tx-pending",
  accountId: checkingAccount.id,
  categoryId: streamingCategory.id,
  type: TransactionType.EXPENSE,
  amountCents: 3990,
  date: "2026-07-15",
  description: "Assinatura de streaming",
  status: TransactionStatus.PENDING,
};

// BE-8: Transaction.tags is optional in the generated type only so
// pre-Phase-2 fixtures keep type-checking — the server always sends it.
export const groceriesTag: Tag = { id: "tag-groceries", name: "mercado" };
export const urgentTag: Tag = { id: "tag-urgent", name: "urgente" };
export const defaultTags: Tag[] = [groceriesTag, urgentTag];

export const taggedExpense: Transaction = {
  id: "tx-tagged",
  accountId: checkingAccount.id,
  categoryId: groceriesCategory.id,
  type: TransactionType.EXPENSE,
  amountCents: 8000,
  date: "2026-07-12",
  description: "Compra marcada",
  status: TransactionStatus.SETTLED,
  tags: [groceriesTag, urgentTag],
};

export const creditCard: Card = {
  id: "card-nubank",
  name: "Nubank",
  brand: "Mastercard",
  limitCents: 500000,
  // limit − unpaid statements (R1): the backend fills this in.
  availableLimitCents: 350000,
  closingDay: 20,
  dueDay: 5,
  defaultPaymentAccountId: checkingAccount.id,
  color: null,
  archived: false,
};

export const archivedCard: Card = {
  id: "card-old",
  name: "Cartão Antigo",
  brand: null,
  limitCents: 100000,
  availableLimitCents: 100000,
  closingDay: 10,
  dueDay: 20,
  defaultPaymentAccountId: null,
  color: null,
  archived: true,
};

export const defaultCards: Card[] = [creditCard];

// A R$ 100,00 purchase in 3× (33,33 / 33,33 / 33,34) landing on this statement,
// mirroring the canonical scenario (CONTEXTO §3).
export const openStatement: Statement = {
  id: "stmt-2026-07",
  cardId: creditCard.id,
  referenceMonth: "2026-07",
  closingDate: "2026-07-20",
  dueDate: "2026-08-05",
  status: StatementStatus.OPEN,
  totalCents: 10000,
  paidCents: 0,
};

export const openStatementDetail: StatementDetail = {
  ...openStatement,
  payments: [],
  items: [
    {
      installmentId: "inst-1",
      purchaseId: "purchase-1",
      description: "Notebook",
      categoryId: groceriesCategory.id,
      amountCents: 3333,
      purchaseDate: "2026-07-18",
      installmentNumber: 1,
      installmentCount: 3,
    },
  ],
};

export const defaultAccounts: Account[] = [checkingAccount, investmentAccount];
export const defaultCategories: Category[] = [
  groceriesCategory,
  streamingCategory,
  salaryCategory,
  statementPaymentCategory,
];

export function transactionPage(content: Transaction[]): TransactionPage {
  return { content, page: 0, size: 20, totalElements: content.length };
}

interface FinanceHandlerOptions {
  accounts?: Account[];
  categories?: Category[];
  transactions?: Transaction[];
  /** Workspace tags, source of the autocomplete (useListTags). */
  tags?: Tag[];
}

/**
 * Read handlers for the lists every FE-2/FE-7 screen depends on. Pass to
 * `server.use(...)`; add write handlers per test to assert payloads.
 *
 * The transactions handler applies the `tags` query filter for real (OR
 * match by name, case-insensitive — apiFetch serializes the array as a
 * single comma-joined value, mirroring how Spring binds List<String> query
 * params), so tests can assert the list actually narrows instead of only
 * capturing the query string.
 */
export function financeReadHandlers({
  accounts = defaultAccounts,
  categories = defaultCategories,
  transactions = [],
  tags = defaultTags,
}: FinanceHandlerOptions = {}) {
  return [
    http.get("*/workspaces/:workspaceId/accounts", ({ request }) => {
      const includeArchived = new URL(request.url).searchParams.get("includeArchived") === "true";
      return HttpResponse.json(
        includeArchived ? accounts : accounts.filter((account) => !account.archived),
      );
    }),
    http.get("*/workspaces/:workspaceId/categories", () => HttpResponse.json(categories)),
    http.get("*/workspaces/:workspaceId/tags", () => HttpResponse.json(tags)),
    http.get("*/workspaces/:workspaceId/transactions", ({ request }) => {
      const tagsParam = new URL(request.url).searchParams.getAll("tags");
      if (tagsParam.length === 0) return HttpResponse.json(transactionPage(transactions));
      const wanted = tagsParam.map((name) => name.trim().toLowerCase());
      const filtered = transactions.filter((transaction) =>
        (transaction.tags ?? []).some((tag) => wanted.includes(tag.name.toLowerCase())),
      );
      return HttpResponse.json(transactionPage(filtered));
    }),
  ];
}

interface CardHandlerOptions {
  cards?: Card[];
  /** Statements keyed by reference month (YYYY-MM); the list is filtered by from=to. */
  statements?: Statement[];
  detail?: StatementDetail;
}

/**
 * Read handlers for the cards + statements screens. The statements list honours
 * the from/to window so month navigation returns the matching statement only.
 */
export function cardReadHandlers({
  cards = defaultCards,
  statements = [openStatement],
  detail = openStatementDetail,
}: CardHandlerOptions = {}) {
  return [
    http.get("*/workspaces/:workspaceId/cards", ({ request }) => {
      const includeArchived = new URL(request.url).searchParams.get("includeArchived") === "true";
      return HttpResponse.json(includeArchived ? cards : cards.filter((card) => !card.archived));
    }),
    http.get("*/workspaces/:workspaceId/cards/:cardId/statements", ({ request }) => {
      const params = new URL(request.url).searchParams;
      const from = params.get("from");
      const to = params.get("to");
      return HttpResponse.json(
        statements.filter(
          (statement) =>
            (!from || statement.referenceMonth >= from) &&
            (!to || statement.referenceMonth <= to),
        ),
      );
    }),
    http.get("*/workspaces/:workspaceId/statements/:statementId", () => HttpResponse.json(detail)),
  ];
}
