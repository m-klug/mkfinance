import { http, HttpResponse } from "msw";

import { getAccountsMock } from "@/lib/api/generated/accounts/accounts.msw";
import { getCardsMock } from "@/lib/api/generated/cards/cards.msw";
import { getCategoriesMock } from "@/lib/api/generated/categories/categories.msw";
import { getDashboardMock } from "@/lib/api/generated/dashboard/dashboard.msw";
import { getExportMock } from "@/lib/api/generated/export/export.msw";
import { getPillarsMock } from "@/lib/api/generated/pillars/pillars.msw";
import { getStatementsMock } from "@/lib/api/generated/statements/statements.msw";
import { getTagsMock } from "@/lib/api/generated/tags/tags.msw";
import { getTransactionsMock } from "@/lib/api/generated/transactions/transactions.msw";
import { getTransfersMock } from "@/lib/api/generated/transfers/transfers.msw";
import { getWorkspacesMock } from "@/lib/api/generated/workspaces/workspaces.msw";
import {
  MemberRole,
  type CurrentUser,
  type LoginRequest,
  type RegisterRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

// The orval-generated auth mocks (getAuthMock) return independent random
// fake users on every call and never fail — /auth/me would always succeed,
// so the auth guard could never be exercised against MSW. These handlers
// replace just the auth tag with a tiny session backed by localStorage:
// register/login persist a user, logout clears it, and /auth/me reflects
// whatever is stored (401 when empty). Response shapes still follow the
// contract types, and every other tag keeps using the generated mocks
// as-is, per FE-1 scope.
export const MOCK_SESSION_KEY = "mk-finance-mock-session";

function readSession(): CurrentUser | null {
  const raw = localStorage.getItem(MOCK_SESSION_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as CurrentUser;
  } catch {
    return null;
  }
}

function writeSession(user: CurrentUser) {
  localStorage.setItem(MOCK_SESSION_KEY, JSON.stringify(user));
}

function clearSession() {
  localStorage.removeItem(MOCK_SESSION_KEY);
}

function buildUser(name: string, email: string): CurrentUser {
  return {
    id: crypto.randomUUID(),
    name,
    email,
    locale: "pt-BR",
    workspaces: [
      { id: crypto.randomUUID(), name: `Espaço de ${name}`, role: MemberRole.OWNER },
    ],
  };
}

const authHandlers = [
  http.post("*/auth/register", async ({ request }) => {
    const body = (await request.json()) as RegisterRequest;
    const user = buildUser(body.name, body.email);
    writeSession(user);
    return HttpResponse.json(user, { status: 201 });
  }),

  http.post("*/auth/login", async ({ request }) => {
    const body = (await request.json()) as LoginRequest;
    const existing = readSession();
    const user = existing ?? buildUser(body.email.split("@")[0] || "Usuário", body.email);
    writeSession(user);
    return HttpResponse.json(user, { status: 200 });
  }),

  http.post("*/auth/logout", () => {
    clearSession();
    return new HttpResponse(null, { status: 204 });
  }),

  http.get("*/auth/me", () => {
    const user = readSession();
    if (!user) {
      return HttpResponse.json({ code: "UNAUTHORIZED" }, { status: 401 });
    }
    return HttpResponse.json(user, { status: 200 });
  }),
];

export const handlers = [
  ...authHandlers,
  ...getAccountsMock(),
  ...getCategoriesMock(),
  ...getWorkspacesMock(),
  ...getPillarsMock(),
  ...getCardsMock(),
  ...getStatementsMock(),
  ...getTagsMock(),
  ...getTransactionsMock(),
  ...getTransfersMock(),
  ...getDashboardMock(),
  ...getExportMock(),
];
