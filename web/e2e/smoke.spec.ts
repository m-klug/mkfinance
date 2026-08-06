import { expect, test, type Page } from "@playwright/test";

// End-to-end smoke of the canonical happy path against the REAL stack (backend
// + Postgres, no MSW): register -> create account -> income entry -> create
// card -> installment card purchase -> pay the statement -> dashboard reflects
// it. This is the one flow that MSW never exercised, so it is where the real
// context-path + CSRF wiring (INT-1 Phase 2) is proven from the browser.
//
// Prerequisites: the API and Postgres must be running (docker compose up, or a
// local spring-boot:run against a Postgres on 5432). The Vite dev server is
// started by playwright.config.ts (VITE_API_MOCK=false).

// R$ 5.000,00 etc. — the money field reads keystrokes as cents (money.ts), so
// "500000" means R$ 5.000,00.
const SALARY = "500000";
const CARD_LIMIT = "1000000"; // R$ 10.000,00
const PURCHASE_TOTAL = "150000"; // R$ 1.500,00, split in 3x

function uniqueEmail(): string {
  return `smoke-${Date.now()}-${Math.floor(Math.random() * 1e6)}@mkfinance.test`;
}

async function fillMoney(page: Page, id: string, digits: string) {
  const field = page.locator(`#${id}`);
  await field.click();
  await field.fill("");
  await field.type(digits);
}

test("register through dashboard: the canonical happy path", async ({ page }) => {
  const email = uniqueEmail();

  // --- Register (first mutation: CSRF must already be primed) ---
  await page.goto("/register");
  await page.locator("#register-name").fill("Smoke Tester");
  await page.locator("#register-email").fill(email);
  await page.locator("#register-password").fill("password123");
  await page.getByRole("button", { name: "Criar conta", exact: true }).click();

  // Lands on the dashboard shell.
  await expect(page.getByRole("heading", { name: "Dashboard", level: 1 })).toBeVisible();
  await expect(page.getByText("Alocação por pilar")).toBeVisible();

  // --- Create a checking account ---
  await page.getByRole("link", { name: "Contas" }).click();
  await page.getByRole("button", { name: "Nova conta" }).click();
  await page.locator("#account-name").fill("Conta Corrente");
  await page.locator("#account-type").selectOption("CHECKING");
  const accountDialog = page.getByRole("dialog");
  await accountDialog.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByRole("cell", { name: "Conta Corrente", exact: true })).toBeVisible();

  // --- Income entry (salary) via quick entry ---
  await page.getByRole("button", { name: "Novo lançamento" }).click();
  let dialog = page.getByRole("dialog");
  await dialog.getByRole("tab", { name: "Receita" }).click();
  await fillMoney(page, "quick-amount", SALARY);
  await page.locator("#quick-description").fill("Salário");
  // First real category option (index 0 is the placeholder).
  await page.locator("#quick-category").selectOption({ index: 1 });
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(dialog).toBeHidden();

  // --- Create a credit card (closing day high enough that today's purchase
  // lands in the current-month statement the page opens on) ---
  await page.getByRole("link", { name: "Cartões" }).click();
  await page.getByRole("button", { name: "Novo cartão" }).click();
  await page.locator("#card-name").fill("Cartão Principal");
  await fillMoney(page, "card-limit", CARD_LIMIT);
  await page.locator("#card-closing-day").selectOption("28");
  await page.locator("#card-due-day").selectOption("5");
  const cardDialog = page.getByRole("dialog");
  await cardDialog.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByRole("cell", { name: "Cartão Principal", exact: true })).toBeVisible();

  // --- Installment card purchase (3x) via quick entry ---
  await page.getByRole("button", { name: "Novo lançamento" }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByRole("tab", { name: "Cartão" }).click();
  await fillMoney(page, "quick-amount", PURCHASE_TOTAL);
  await page.locator("#quick-description").fill("Notebook");
  await page.locator("#quick-category").selectOption({ index: 1 });
  await page.locator("#quick-installments").selectOption("3");
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(dialog).toBeHidden();

  // --- Open the card statement and pay it in full ---
  await page.getByRole("link", { name: /fatura/i }).first().click();
  // The first installment (1/3 = R$ 500,00) shows up in this month's statement.
  await expect(page.getByRole("cell", { name: "Notebook" }).first()).toBeVisible();
  await expect(page.getByText("1/3")).toBeVisible();

  await page.getByRole("button", { name: "Pagar fatura" }).click();
  const payDialog = page.getByRole("dialog");
  await payDialog.locator("#payment-account").selectOption({ label: "Conta Corrente" });
  await payDialog.getByRole("button", { name: "Pagar", exact: true }).click();
  await expect(payDialog).toBeHidden();
  // Payment recorded — the payments section now lists it.
  await expect(page.getByText("Pagamentos")).toBeVisible();

  // --- Dashboard confirms the movement ---
  // Full load (not a client-side nav) so the summary/allocation queries fetch
  // fresh server truth rather than the staleTime-cached empty snapshot from the
  // immediate post-register render.
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Dashboard", level: 1 })).toBeVisible();
  await expect(page.getByText("Alocação por pilar")).toBeVisible();
  // Income was registered, so the allocation panel is NOT in its no-income
  // state (that warning only shows when the month has zero income).
  await expect(
    page.getByText("Sem receita registrada neste mês", { exact: false }),
  ).toHaveCount(0);
  // The statement payment created an expense on the checking account, so the
  // balance panel renders the account.
  await expect(page.getByText("Conta Corrente").first()).toBeVisible();
});

// INT-2 — the Phase 2 surface end to end: edit the pillar targets, let a
// recurrence materialize into a pending entry and settle it in one click, then
// tag a transaction and filter the extrato by that tag. Same real stack, no MSW.
test("phase 2: pillar targets, recurrence settle and tag filter", async ({ page }) => {
  const email = uniqueEmail();

  await page.goto("/register");
  await page.locator("#register-name").fill("Phase Two Tester");
  await page.locator("#register-email").fill(email);
  await page.locator("#register-password").fill("password123");
  await page.getByRole("button", { name: "Criar conta", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Dashboard", level: 1 })).toBeVisible();

  await page.getByRole("link", { name: "Contas" }).click();
  await page.getByRole("button", { name: "Nova conta" }).click();
  await page.locator("#account-name").fill("Conta Corrente");
  await page.locator("#account-type").selectOption("CHECKING");
  await page.getByRole("dialog").getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByRole("cell", { name: "Conta Corrente", exact: true })).toBeVisible();

  // --- Edit the pillar targets (BE-6 + FE-5) ---
  await page.getByRole("link", { name: "Pilares" }).click();
  await expect(page.getByRole("heading", { name: "Configurações de pilares" })).toBeVisible();

  // Move 5 points from PLEASURES to KNOWLEDGE; the sum stays 100 so save unlocks.
  await page.locator("#pillar-target-PLEASURES").fill("5");
  await page.locator("#pillar-target-KNOWLEDGE").fill("10");
  await expect(page.getByText("Soma: 100%")).toBeVisible();
  await page.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByText("Percentuais-alvo atualizados.")).toBeVisible();

  // A sum other than 100 must block the submit — the client guard, with the
  // server rule (PILLAR_TARGETS_MUST_SUM_100) still the authority behind it.
  await page.locator("#pillar-target-KNOWLEDGE").fill("40");
  await expect(page.getByRole("button", { name: "Salvar" })).toBeDisabled();
  await page.locator("#pillar-target-KNOWLEDGE").fill("10");

  // --- Create a monthly recurrence starting today (BE-7 + FE-6) ---
  const today = new Date();
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  const firstOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);
  const lastOfMonth = new Date(today.getFullYear(), today.getMonth() + 1, 0);

  await page.getByRole("link", { name: "Recorrências" }).click();
  await page.getByRole("button", { name: "Nova recorrência" }).click();
  const recurrenceDialog = page.getByRole("dialog");
  await recurrenceDialog.locator("#recurrence-type").selectOption("INCOME");
  await recurrenceDialog.locator("#recurrence-account").selectOption({ label: "Conta Corrente" });
  await recurrenceDialog.locator("#recurrence-category").selectOption({ index: 1 });
  await fillMoney(page, "recurrence-amount", SALARY);
  await recurrenceDialog.locator("#recurrence-description").fill("Salário recorrente");
  await recurrenceDialog.locator("#recurrence-frequency").selectOption("MONTHLY");
  // The LAST day of the month on purpose: on any day but the 31st this occurrence is
  // in the future, so it only reaches the screen because the extrato now opens on the
  // whole competency instead of stopping at today. With the old default this line is
  // what makes the test fail.
  await recurrenceDialog.locator("#recurrence-day-of-month").fill(String(lastOfMonth.getDate()));
  await recurrenceDialog.locator("#recurrence-start-date").fill(iso(firstOfMonth));
  await recurrenceDialog.getByRole("button", { name: "Salvar" }).click();
  await expect(recurrenceDialog).toBeHidden();
  await expect(page.getByText("Salário recorrente").first()).toBeVisible();

  // --- The calendar shows the occurrence (read-only view, FE-6) ---
  await page.getByRole("link", { name: "Calendário" }).click();
  await expect(page.getByText("Salário recorrente").first()).toBeVisible();

  // --- The extrato materialized it as PENDING; settle it in one click (R5) ---
  // No filter is touched here on purpose: the row has to be visible in the view the
  // extrato opens on, which is the whole point of the fix.
  await page.getByRole("link", { name: "Lançamentos" }).click();
  const pendingRow = page.getByRole("row", { name: /Salário recorrente/ }).first();
  await expect(pendingRow).toBeVisible();
  await pendingRow.getByRole("button", { name: /^Efetivar/ }).click();
  await expect(page.getByText("Lançamento efetivado.")).toBeVisible();

  // --- Tag a transaction and filter by it (BE-8 + FE-7) ---
  // The extrato renders its own "Novo lançamento" besides the one in the header.
  await page.getByRole("main").getByRole("button", { name: "Novo lançamento" }).click();
  const quickDialog = page.getByRole("dialog");
  await fillMoney(page, "quick-amount", "12345");
  await page.locator("#quick-description").fill("Mercado da esquina");
  await page.locator("#quick-category").selectOption({ index: 1 });
  await quickDialog.getByPlaceholder("Adicionar tag e pressionar Enter").fill("mercado");
  await quickDialog.getByPlaceholder("Adicionar tag e pressionar Enter").press("Enter");
  await quickDialog.getByRole("button", { name: "Salvar" }).click();
  await expect(quickDialog).toBeHidden();

  // Row-level assertions: a description matches several cells (the description
  // itself, and the action cell's "Editar <description>" label).
  const groceryRow = page.getByRole("row", { name: /Mercado da esquina/ });
  const salaryRow = page.getByRole("row", { name: /Salário recorrente/ });
  await expect(groceryRow).toHaveCount(1);
  // The tag chip renders on the row, which is what makes the filter meaningful.
  await expect(groceryRow).toContainText("mercado");

  // Filtering by the tag narrows the list to the tagged entry...
  await page.locator("#filter-tags").fill("mercado");
  await page.locator("#filter-tags").press("Enter");
  await expect(groceryRow).toHaveCount(1);
  await expect(salaryRow).toHaveCount(0);

  // ...and clearing it brings the rest back.
  await page.getByRole("button", { name: /Remover tag mercado/ }).click();
  await expect(salaryRow).toHaveCount(1);
});

// INT-3 — the caixinhas surface end to end: create an investment account with a
// pre-existing balance, create a vault with a goal, allocate that existing balance
// through a reallocation (null -> vault, §4.7), contribute choosing the vault, confirm
// the dashboard credits the vault's pillar (not the account's default one), then book
// a yield and see it split into the vault. Same real stack, no MSW.
test("caixinhas: allocate existing balance, contribute by vault, dashboard credit, yield split", async ({
  page,
}) => {
  const email = uniqueEmail();

  await page.goto("/register");
  await page.locator("#register-name").fill("Vaults Tester");
  await page.locator("#register-email").fill(email);
  await page.locator("#register-password").fill("password123");
  await page.getByRole("button", { name: "Criar conta", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Dashboard", level: 1 })).toBeVisible();

  // --- A checking account (the aporte's origin) ---
  await page.getByRole("link", { name: "Contas" }).click();
  await page.getByRole("button", { name: "Nova conta" }).click();
  let dialog = page.getByRole("dialog");
  await page.locator("#account-name").fill("Conta Corrente");
  await page.locator("#account-type").selectOption("CHECKING");
  await fillMoney(page, "account-initial-balance", "2000000"); // R$ 20.000,00
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByRole("cell", { name: "Conta Corrente", exact: true })).toBeVisible();

  // --- An investment account that already has money in it (§4.7's "quem já tem
  // R$ 50 mil na conta" scenario, scaled down) ---
  await page.getByRole("button", { name: "Nova conta" }).click();
  dialog = page.getByRole("dialog");
  await page.locator("#account-name").fill("Corretora");
  await page.locator("#account-type").selectOption("INVESTMENT");
  await fillMoney(page, "account-initial-balance", "500000"); // R$ 5.000,00
  await page.locator("#account-contribution-pillar").selectOption("FINANCIAL_FREEDOM");
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByRole("cell", { name: "Corretora", exact: true })).toBeVisible();

  // --- Open the account's vaults screen (the "Caixinhas de <conta>" row action) ---
  await page.getByRole("link", { name: "Caixinhas de Corretora" }).click();
  await expect(page.getByRole("heading", { name: "Corretora", level: 1 })).toBeVisible();
  // No vault yet: the whole R$ 5.000,00 already sits in the unallocated slice.
  await expect(page.getByText(/Não alocado/).first()).toBeVisible();
  await expect(page.getByText(/5\.000,00/).first()).toBeVisible();

  // --- Create a vault with a goal (GOALS pillar, distinct from the account's
  // own FINANCIAL_FREEDOM default — the point of the whole scenario is proving
  // the pillar comes from the vault, not the account) ---
  await page.getByRole("button", { name: "Nova caixinha" }).click();
  dialog = page.getByRole("dialog");
  await page.locator("#vault-name").fill("Viagem");
  await page.locator("#vault-pillar").selectOption("GOALS");
  await fillMoney(page, "vault-target-amount", "1200000"); // R$ 12.000,00
  await page.locator("#vault-target-date").fill("2027-12-31");
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText("Viagem")).toBeVisible();

  // --- Allocate part of the pre-existing balance into the vault (null -> Viagem,
  // §4.7's third row — the onboarding path for money that predates any vault) ---
  await page.getByRole("button", { name: "Remanejar" }).click();
  dialog = page.getByRole("dialog");
  // "De" is left as "Não alocado" (the default, empty-string option) on purpose.
  await dialog.locator("#reallocate-to").selectOption({ label: "Viagem" });
  await fillMoney(page, "reallocate-amount", "300000"); // R$ 3.000,00
  await dialog.getByRole("button", { name: "Remanejar", exact: true }).click();
  await expect(dialog).toBeHidden();
  const viagemRow = page.locator("li").filter({ hasText: "Viagem" }).first();
  await expect(viagemRow).toContainText("3.000,00");
  // The account's own balance never moved — a reallocation is bookkeeping only.
  await expect(page.getByText(/5\.000,00/).first()).toBeVisible();

  // --- Contribute choosing the vault, from the header quick entry ---
  await page.getByRole("button", { name: "Novo lançamento" }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByRole("tab", { name: "Aporte" }).click();
  await dialog.locator("#quick-account").selectOption({ label: "Conta Corrente" });
  await dialog.locator("#quick-to-account").selectOption({ label: "Corretora" });
  await dialog.locator("#quick-to-vault").selectOption({ label: "Viagem" });
  await fillMoney(page, "quick-amount", "50000"); // R$ 500,00
  await page.locator("#quick-description").fill("Aporte viagem");
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(dialog).toBeHidden();

  // The header's quick entry only invalidates the transactions/accounts caches
  // (invalidateAfterWrite in QuickTransactionDialog), not the vaults composition
  // query this page holds — a genuine 30s staleTime gap (main.tsx), same class of
  // issue the canonical happy-path test above works around for the dashboard. A
  // full reload bypasses the client-side cache instead of racing it.
  await page.reload();
  await expect(page.getByRole("heading", { name: "Corretora", level: 1 })).toBeVisible();

  // The vault grew by exactly the contribution (3.000,00 + 500,00 = 3.500,00).
  await expect(viagemRow).toContainText("3.500,00");

  // --- The dashboard credits the vault's pillar (GOALS), not the account's own
  // default (FINANCIAL_FREEDOM) — the whole point of the caixinhas feature ---
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Dashboard", level: 1 })).toBeVisible();
  await expect(page.getByText("Alocação por pilar")).toBeVisible();
  const goalsRow = page.locator("li").filter({ hasText: "Metas" }).first();
  await expect(goalsRow).toContainText("500,00");
  // FINANCIAL_FREEDOM (Liberdade Financeira) got nothing from this contribution —
  // only the reallocation's account-level no-op and the vault's own pillar moved.
  const freedomRow = page.locator("li").filter({ hasText: "Liberdade Financeira" }).first();
  await expect(freedomRow).toContainText("R$ 0,00");

  // --- Book a yield and see the split (§4.5): with a single vault holding all the
  // money, it takes the whole yield ---
  await page.getByRole("link", { name: "Contas" }).click();
  await page.getByRole("link", { name: "Caixinhas de Corretora" }).click();
  await page.getByRole("button", { name: "Lançar rendimento" }).click();
  dialog = page.getByRole("dialog");
  await fillMoney(page, "vault-yield-amount", "10000"); // R$ 100,00
  await page.locator("#vault-yield-description").fill("Rendimento de agosto");
  // The preview mirrors YieldDistributor locally, before the server confirms it.
  await expect(dialog.getByText("Prévia do rateio")).toBeVisible();
  await expect(dialog.getByText("Viagem")).toBeVisible();
  await dialog.getByRole("button", { name: "Lançar rendimento", exact: true }).click();
  await expect(dialog).toBeHidden();

  // The vault absorbed the whole yield: 3.500,00 + 100,00 = 3.600,00.
  await expect(viagemRow).toContainText("3.600,00");
});
