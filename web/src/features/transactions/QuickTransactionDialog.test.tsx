import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import { QuickTransactionDialog } from "@/features/transactions/QuickTransactionDialog";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  checkingAccount,
  financeReadHandlers,
  groceriesCategory,
  groceriesTag,
  investmentAccount,
  salaryCategory,
  settledExpense,
  urgentTag,
} from "@/test/financeFixtures";
import { reserveVault, tripVault, vaultReadHandlers } from "@/test/vaultFixtures";
import {
  ContributionPillar,
  type TransactionRequest,
  type TransferRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

function renderDialog() {
  seedAuthenticatedSession();
  return renderWithProviders(<QuickTransactionDialog open onOpenChange={() => {}} />);
}

function captureTransactions(sink: TransactionRequest[]) {
  return http.post("*/workspaces/:workspaceId/transactions", async ({ request }) => {
    sink.push((await request.json()) as TransactionRequest);
    return HttpResponse.json(settledExpense, { status: 201 });
  });
}

function captureTransfers(sink: TransferRequest[]) {
  return http.post("*/workspaces/:workspaceId/transfers", async ({ request }) => {
    sink.push((await request.json()) as TransferRequest);
    return HttpResponse.json(settledExpense, { status: 201 });
  });
}

describe("QuickTransactionDialog", () => {
  beforeEach(() => {
    server.use(...financeReadHandlers());
  });

  it("registers an expense in four interactions, with account and date prefilled", async () => {
    const calls: TransactionRequest[] = [];
    server.use(captureTransactions(calls));
    const user = userEvent.setup();
    renderDialog();

    // Expense is the default tab, so no interaction is spent choosing it.
    await screen.findByRole("tab", { name: "Despesa", selected: true });

    // 1) amount, 2) description, 3) category, 4) save.
    await user.type(screen.getByLabelText("Valor"), "4599");
    await user.type(screen.getByLabelText("Descrição"), "Padaria");
    await user.selectOptions(screen.getByLabelText("Categoria"), groceriesCategory.id);
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toMatchObject({
      accountId: checkingAccount.id,
      categoryId: groceriesCategory.id,
      type: "EXPENSE",
      amountCents: 4599,
      description: "Padaria",
      status: "SETTLED",
    });
    expect(calls[0].date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  // BE-8/FE-7: tags are optional and off the ≤5-interaction critical path —
  // this test only asserts they are sent when the user does add them.
  it("registers an expense with two tags", async () => {
    const calls: TransactionRequest[] = [];
    server.use(captureTransactions(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.type(await screen.findByLabelText("Valor"), "3000");
    await user.type(screen.getByLabelText("Descrição"), "Compra com tags");
    await user.selectOptions(screen.getByLabelText("Categoria"), groceriesCategory.id);

    const tagsField = screen.getByLabelText("Tags");
    await user.type(tagsField, `${groceriesTag.name}{Enter}`);
    await user.type(tagsField, `${urgentTag.name}{Enter}`);

    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].tags).toEqual([groceriesTag.name, urgentTag.name]);
  });

  it("does not require any tag to register an expense (kept off the critical path)", async () => {
    const calls: TransactionRequest[] = [];
    server.use(captureTransactions(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.type(await screen.findByLabelText("Valor"), "3000");
    await user.type(screen.getByLabelText("Descrição"), "Sem tags");
    await user.selectOptions(screen.getByLabelText("Categoria"), groceriesCategory.id);
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].tags).toEqual([]);
  });

  it("offers income categories on the income tab", async () => {
    const calls: TransactionRequest[] = [];
    server.use(captureTransactions(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Receita" }));
    await user.type(screen.getByLabelText("Valor"), "500000");
    await user.type(screen.getByLabelText("Descrição"), "Salário de julho");
    await user.selectOptions(screen.getByLabelText("Categoria"), salaryCategory.id);
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toMatchObject({ type: "INCOME", amountCents: 500000 });
  });

  it("can schedule the entry as pending", async () => {
    const calls: TransactionRequest[] = [];
    server.use(captureTransactions(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.type(await screen.findByLabelText("Valor"), "8000");
    await user.type(screen.getByLabelText("Descrição"), "Conta de luz");
    await user.selectOptions(screen.getByLabelText("Categoria"), groceriesCategory.id);
    await user.click(screen.getByLabelText("Agendar"));
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].status).toBe("PENDING");
  });

  it("blocks submission when the amount is zero or the description is empty", async () => {
    const calls: TransactionRequest[] = [];
    server.use(captureTransactions(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Informe um valor maior que zero.")).toBeInTheDocument();
    expect(screen.getAllByText("Campo obrigatório.").length).toBeGreaterThan(0);
    expect(calls).toHaveLength(0);
  });

  it("shows the contribution pillar prefilled with the destination account default (R9)", async () => {
    const calls: TransferRequest[] = [];
    server.use(captureTransfers(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Aporte" }));

    // The destination defaults to the only INVESTMENT account, whose default
    // contribution pillar is GOALS — so the selector comes pre-filled with it.
    const pillarSelect = await screen.findByLabelText("Pilar do aporte");
    expect(pillarSelect).toHaveValue(ContributionPillar.GOALS);
    expect(screen.getByLabelText("Conta de destino")).toHaveValue(investmentAccount.id);

    await user.type(screen.getByLabelText("Valor"), "100000");
    await user.type(screen.getByLabelText("Descrição"), "Aporte mensal");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toMatchObject({
      fromAccountId: checkingAccount.id,
      toAccountId: investmentAccount.id,
      amountCents: 100000,
      contributionPillar: ContributionPillar.GOALS,
    });
  });

  it("lets the contribution pillar be overridden for a single entry (R9)", async () => {
    const calls: TransferRequest[] = [];
    server.use(captureTransfers(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Aporte" }));
    await user.selectOptions(
      await screen.findByLabelText("Pilar do aporte"),
      ContributionPillar.FINANCIAL_FREEDOM,
    );
    await user.type(screen.getByLabelText("Valor"), "20000");
    await user.type(screen.getByLabelText("Descrição"), "Reserva");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].contributionPillar).toBe(ContributionPillar.FINANCIAL_FREEDOM);
  });

  // The three pillars whose money is set aside before being spent (R9) — study
  // money is saved up for a course exactly the way a goal is.
  it("offers Conhecimento as a contribution pillar", async () => {
    const calls: TransferRequest[] = [];
    server.use(captureTransfers(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Aporte" }));
    const pillar = await screen.findByLabelText("Pilar do aporte");
    expect(within(pillar).getByRole("option", { name: "Conhecimento" })).toBeInTheDocument();

    await user.selectOptions(pillar, ContributionPillar.KNOWLEDGE);
    await user.type(screen.getByLabelText("Valor"), "30000");
    await user.type(screen.getByLabelText("Descrição"), "Reserva de cursos");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0].contributionPillar).toBe(ContributionPillar.KNOWLEDGE);
  });

  it("hides the pillar selector on a plain transfer between non-investment accounts", async () => {
    const secondChecking = {
      ...checkingAccount,
      id: "acc-checking-2",
      name: "Conta Secundária",
    };
    server.use(...financeReadHandlers({ accounts: [checkingAccount, secondChecking] }));
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Transferência" }));

    await waitFor(() =>
      expect(screen.getByLabelText("Conta de destino")).toHaveValue(secondChecking.id),
    );
    // R4: a plain transfer is neither income nor expense and consumes no pillar.
    expect(screen.queryByLabelText("Pilar do aporte")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Categoria")).not.toBeInTheDocument();
  });

  it("moves the destination away when the origin is set to the same account", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Transferência" }));
    await waitFor(() =>
      expect(screen.getByLabelText("Conta de destino")).toHaveValue(investmentAccount.id),
    );

    // Picking the current destination as origin must not leave both sides equal.
    await user.selectOptions(screen.getByLabelText("Conta de origem"), investmentAccount.id);

    await waitFor(() =>
      expect(screen.getByLabelText("Conta de destino")).toHaveValue(checkingAccount.id),
    );
  });

  it("refuses a transfer without a destination account", async () => {
    const calls: TransferRequest[] = [];
    server.use(...financeReadHandlers({ accounts: [checkingAccount] }), captureTransfers(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Transferência" }));
    await user.type(screen.getByLabelText("Valor"), "1000");
    await user.type(screen.getByLabelText("Descrição"), "Ida e volta");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(
      await screen.findByText("Selecione a conta de origem e a de destino."),
    ).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });
});

// BE-10/§4.4: once an account has any active vault, every leg touching it
// must name one, and the pillar stops being an editable field for the
// destination — it comes from the vault chosen.
describe("QuickTransactionDialog — vaults (§4.4)", () => {
  const vaultedInvestment = { ...investmentAccount, hasVaults: true };

  beforeEach(() => {
    server.use(
      ...financeReadHandlers({ accounts: [checkingAccount, vaultedInvestment] }),
      ...vaultReadHandlers(),
    );
  });

  it("requires the destination vault before submitting an aporte, and derives the pillar from it instead of offering the selector", async () => {
    const calls: TransferRequest[] = [];
    server.use(captureTransfers(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Aporte" }));
    await screen.findByLabelText("Caixinha de destino");
    expect(screen.queryByLabelText("Pilar do aporte")).not.toBeInTheDocument();
    expect(screen.getByText("Selecione uma caixinha para ver o pilar.")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Valor"), "50000");
    await user.type(screen.getByLabelText("Descrição"), "Aporte de agosto");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Selecione uma caixinha.")).toBeInTheDocument();
    expect(calls).toHaveLength(0);

    await user.selectOptions(screen.getByLabelText("Caixinha de destino"), reserveVault.id);
    // The pillar shown switches to the vault's own — Reserva is FINANCIAL_FREEDOM.
    expect(screen.getByText("Liberdade Financeira")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toMatchObject({
      toVaultId: reserveVault.id,
      contributionPillar: null,
    });
  });

  it("requires the origin vault for a plain transfer out of a vaulted account (resgate)", async () => {
    const calls: TransferRequest[] = [];
    server.use(captureTransfers(calls));
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Transferência" }));
    await user.selectOptions(
      await screen.findByLabelText("Conta de origem"),
      vaultedInvestment.id,
    );

    expect(await screen.findByLabelText("Caixinha de origem")).toBeInTheDocument();
    // The destination (moved away from the origin automatically) has no
    // vault, so no destination vault field or derived pillar shows up.
    expect(screen.queryByLabelText("Caixinha de destino")).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Valor"), "20000");
    await user.type(screen.getByLabelText("Descrição"), "Resgate parcial");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Selecione uma caixinha.")).toBeInTheDocument();
    expect(calls).toHaveLength(0);

    await user.selectOptions(screen.getByLabelText("Caixinha de origem"), tripVault.id);
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toMatchObject({ fromVaultId: tripVault.id, toVaultId: null });
  });
});
