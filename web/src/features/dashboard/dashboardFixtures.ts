import { http, HttpResponse } from "msw";

import {
  Pillar,
  StatementStatus,
  type Card,
  type DashboardSummary,
  type PillarAllocationReport,
  type PillarAllocationReportPillarsItem,
  type Statement,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import {
  checkingAccount,
  defaultCategories,
  groceriesCategory,
  investmentAccount,
  streamingCategory,
} from "@/test/financeFixtures";

// Deterministic dashboard data for the render tests. The orval MSW mock answers
// with random faker after a 1s delay (CONTEXTO §7.5), useless for assertions.

const MONTH = "2026-07";

export const nubankCard: Card = {
  id: "card-nubank",
  name: "Cartão Nubank",
  limitCents: 500000,
  closingDay: 20,
  dueDay: 5,
  archived: false,
};

export const openStatement: Statement = {
  id: "stmt-open",
  cardId: nubankCard.id,
  referenceMonth: MONTH,
  closingDate: "2026-07-20",
  dueDate: "2026-08-05",
  status: StatementStatus.OPEN,
  totalCents: 120000,
  paidCents: 40000,
};

export const dashboardSummary: DashboardSummary = {
  month: MONTH,
  totalBalanceCents: 321000,
  totalBalanceWithInvestmentsCents: 987000,
  incomeCents: 1000000,
  expenseCents: 500000,
  previousMonthIncomeCents: 900000,
  previousMonthExpenseCents: 450000,
  accounts: [checkingAccount, investmentAccount],
  expenseByCategory: [
    { categoryId: groceriesCategory.id, pillar: Pillar.FIXED_COSTS, amountCents: 250000 },
    { categoryId: streamingCategory.id, pillar: Pillar.PLEASURES, amountCents: 50000 },
  ],
  openStatements: [openStatement],
};

// Income R$ 10.000,00 — spentCents / income * 100 gives actualPercent.
const normalPillars: PillarAllocationReportPillarsItem[] = [
  { pillar: Pillar.FIXED_COSTS, targetPercent: 30, spentCents: 250000, actualPercent: 25 },
  { pillar: Pillar.COMFORT, targetPercent: 10, spentCents: 80000, actualPercent: 8 },
  { pillar: Pillar.GOALS, targetPercent: 20, spentCents: 150000, actualPercent: 15 },
  { pillar: Pillar.PLEASURES, targetPercent: 10, spentCents: 50000, actualPercent: 5 },
  { pillar: Pillar.FINANCIAL_FREEDOM, targetPercent: 25, spentCents: 200000, actualPercent: 20 },
  { pillar: Pillar.KNOWLEDGE, targetPercent: 5, spentCents: 30000, actualPercent: 3 },
];

export const normalAllocation: PillarAllocationReport = {
  month: MONTH,
  incomeCents: 1000000,
  noIncome: false,
  pillars: normalPillars,
};

// PLEASURES over its 10% target (15%).
export const overrunAllocation: PillarAllocationReport = {
  month: MONTH,
  incomeCents: 1000000,
  noIncome: false,
  pillars: normalPillars.map((pillar) =>
    pillar.pillar === Pillar.PLEASURES
      ? { ...pillar, spentCents: 150000, actualPercent: 15 }
      : pillar,
  ),
};

// R10: no income → percents are null, only absolute values shown.
export const noIncomeAllocation: PillarAllocationReport = {
  month: MONTH,
  incomeCents: 0,
  noIncome: true,
  pillars: normalPillars.map((pillar) => ({
    ...pillar,
    actualPercent: null,
  })),
};

interface DashboardHandlerOptions {
  summary?: DashboardSummary;
  allocation: PillarAllocationReport;
}

/** Read handlers for every query DashboardPage fires. Pass to `server.use(...)`. */
export function dashboardHandlers({ summary = dashboardSummary, allocation }: DashboardHandlerOptions) {
  return [
    http.get("*/workspaces/:workspaceId/dashboard/summary", () => HttpResponse.json(summary)),
    http.get("*/workspaces/:workspaceId/dashboard/pillar-allocation", () =>
      HttpResponse.json(allocation),
    ),
    http.get("*/workspaces/:workspaceId/categories", () => HttpResponse.json(defaultCategories)),
    http.get("*/workspaces/:workspaceId/cards", () => HttpResponse.json([nubankCard])),
  ];
}
