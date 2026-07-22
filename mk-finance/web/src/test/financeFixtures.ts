import { http, HttpResponse } from "msw";

import {
  AccountType,
  CategoryType,
  ContributionPillar,
  Pillar,
  TransactionStatus,
  TransactionType,
  type Account,
  type Category,
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
}

/**
 * Read handlers for the three lists every FE-2 screen depends on. Pass to
 * `server.use(...)`; add write handlers per test to assert payloads.
 */
export function financeReadHandlers({
  accounts = defaultAccounts,
  categories = defaultCategories,
  transactions = [],
}: FinanceHandlerOptions = {}) {
  return [
    http.get("*/workspaces/:workspaceId/accounts", ({ request }) => {
      const includeArchived = new URL(request.url).searchParams.get("includeArchived") === "true";
      return HttpResponse.json(
        includeArchived ? accounts : accounts.filter((account) => !account.archived),
      );
    }),
    http.get("*/workspaces/:workspaceId/categories", () => HttpResponse.json(categories)),
    http.get("*/workspaces/:workspaceId/transactions", () =>
      HttpResponse.json(transactionPage(transactions)),
    ),
  ];
}
