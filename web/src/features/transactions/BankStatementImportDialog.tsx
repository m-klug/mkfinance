import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { SelectField, TextField } from "@/components/form/fields";
import { isContributionAccount } from "@/features/accounts/accountOptions";
import { useFormatCents } from "@/hooks/useFormatCents";
import { formatDate, formatMonth } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import {
  useConfirmBankStatementImport,
  usePreviewBankStatementImport,
} from "@/lib/api/generated/accounts/accounts";
import type {
  Account,
  BankStatementImportPreview,
  BankStatementImportRequestItemsItem,
  Category,
  ContributionPillar,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

interface BankStatementImportDialogProps {
  workspaceId: string;
  accounts: Account[];
  categories: Category[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImported: () => void;
}

type Line = BankStatementImportPreview["lines"][number];
type Mode = Line["mode"];

interface Row {
  line: Line;
  selected: boolean;
  mode: Mode;
  description: string;
  categoryId: string;
  counterpartAccountId: string;
  statementId: string;
  pillar: string;
}

function toRows(preview: BankStatementImportPreview): Row[] {
  return preview.lines.map((line) => ({
    line,
    selected: line.selectedByDefault,
    mode: line.mode,
    description: line.description,
    categoryId: line.suggestedCategoryId ?? "",
    counterpartAccountId: line.suggestedCounterpartAccountId ?? "",
    statementId: line.suggestedStatementId ?? "",
    pillar: "",
  }));
}

function isComplete(row: Row): boolean {
  if (row.description.trim() === "") return false;
  switch (row.mode) {
    case "ENTRY":
      return row.categoryId !== "";
    case "TRANSFER":
      return row.counterpartAccountId !== "";
    case "STATEMENT_PAYMENT":
      return row.statementId !== "";
  }
}

/** Signed effect of the row on the imported account: what the bank moved, no matter the mode. */
function signedCents(row: Row): number {
  return row.line.type === "INCOME" ? row.line.amountCents : -row.line.amountCents;
}

function toItem(row: Row): BankStatementImportRequestItemsItem {
  return {
    externalId: row.line.externalId,
    mode: row.mode,
    type: row.line.type,
    amountCents: row.line.amountCents,
    date: row.line.date,
    description: row.description.trim(),
    categoryId: row.mode === "ENTRY" ? row.categoryId : null,
    counterpartAccountId: row.mode === "TRANSFER" ? row.counterpartAccountId : null,
    contributionPillar:
      row.mode === "TRANSFER" && row.line.type === "EXPENSE" && row.pillar !== ""
        ? (row.pillar as ContributionPillar)
        : null,
    statementId: row.mode === "STATEMENT_PAYMENT" ? row.statementId : null,
  };
}

export function BankStatementImportDialog({
  workspaceId,
  accounts,
  categories,
  open,
  onOpenChange,
  onImported,
}: BankStatementImportDialogProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const [accountId, setAccountId] = useState("");
  const [file, setFile] = useState<File | undefined>();
  const [fileError, setFileError] = useState<string | undefined>();
  const [preview, setPreview] = useState<BankStatementImportPreview | undefined>();
  const [rows, setRows] = useState<Row[]>([]);

  const previewMutation = usePreviewBankStatementImport();
  const confirmMutation = useConfirmBankStatementImport();

  const activeAccounts = useMemo(() => accounts.filter((account) => !account.archived), [accounts]);
  const accountById = useMemo(
    () => new Map(activeAccounts.map((account) => [account.id, account])),
    [activeAccounts],
  );
  const effectiveAccountId =
    accountId || activeAccounts.find((account) => account.type === "CHECKING")?.id || "";

  // The file never outlives the dialog.
  useEffect(() => {
    if (open) return;
    setAccountId("");
    setFile(undefined);
    setFileError(undefined);
    setPreview(undefined);
    setRows([]);
  }, [open]);

  const categoriesByType = useMemo(
    () => ({
      INCOME: categories.filter((category) => category.type === "INCOME" && !category.system),
      EXPENSE: categories.filter((category) => category.type === "EXPENSE" && !category.system),
    }),
    [categories],
  );

  const selectedRows = rows.filter((row) => row.selected);
  const incomplete = selectedRows.some((row) => !isComplete(row));
  const income = selectedRows
    .filter((row) => row.line.type === "INCOME")
    .reduce((sum, row) => sum + row.line.amountCents, 0);
  const expense = selectedRows
    .filter((row) => row.line.type === "EXPENSE")
    .reduce((sum, row) => sum + row.line.amountCents, 0);

  const allNewSelected =
    rows.length > 0 && rows.every((row) => row.selected || row.line.duplicate === "EXACT");
  const balanceAfter = preview
    ? preview.accountBalanceCents + selectedRows.reduce((sum, row) => sum + signedCents(row), 0)
    : 0;
  const balanceDiffers =
    preview !== undefined &&
    preview.ledgerBalanceCents != null &&
    allNewSelected &&
    balanceAfter !== preview.ledgerBalanceCents;

  function handleRead(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file || !effectiveAccountId) {
      setFileError(t("validation.required"));
      return;
    }
    setFileError(undefined);
    previewMutation.mutate(
      { workspaceId, accountId: effectiveAccountId, data: { file } },
      {
        onSuccess: (result) => {
          setPreview(result);
          setRows(toRows(result));
        },
        onError: (error) => toast.error(translateApiError(t, error)),
      },
    );
  }

  function updateRow(index: number, patch: Partial<Omit<Row, "line">>) {
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  function handleImport() {
    if (!preview || selectedRows.length === 0 || incomplete) return;
    confirmMutation.mutate(
      { workspaceId, accountId: effectiveAccountId, data: { items: selectedRows.map(toItem) } },
      {
        onSuccess: () => {
          toast.success(t("bankImport.done", { count: selectedRows.length }));
          onImported();
          onOpenChange(false);
        },
        onError: (error) => toast.error(translateApiError(t, error)),
      },
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto rounded-modal sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle className="text-[15px] font-extrabold tracking-tight text-ink-primary">
            {t("bankImport.title")}
          </DialogTitle>
          <DialogDescription className="text-ink-tertiary">
            {preview
              ? t("bankImport.reviewSubtitle", {
                  account: accountById.get(effectiveAccountId)?.name ?? "",
                  last4: preview.accountLast4 ?? "----",
                  bank: preview.bankId ?? "---",
                })
              : t("bankImport.uploadSubtitle")}
          </DialogDescription>
        </DialogHeader>

        {!preview && (
          <form className="flex flex-col gap-4" onSubmit={handleRead} noValidate>
            <SelectField
              id="bank-import-account"
              label={t("bankImport.account")}
              options={activeAccounts.map((account) => ({ value: account.id, label: account.name }))}
              value={effectiveAccountId}
              onChange={(event) => setAccountId(event.target.value)}
            />
            <TextField
              id="bank-import-file"
              label={t("bankImport.file")}
              hint={t("bankImport.fileHint")}
              type="file"
              accept=".ofx,application/x-ofx"
              error={fileError}
              onChange={(event) => setFile(event.target.files?.[0])}
            />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" disabled={previewMutation.isPending}>
                {previewMutation.isPending ? t("bankImport.reading") : t("bankImport.read")}
              </Button>
            </DialogFooter>
          </form>
        )}

        {preview && (
          <div className="flex flex-col gap-3">
            {preview.periodStart && preview.periodEnd && (
              <p className="text-xs text-ink-tertiary">
                {t("bankImport.period", {
                  from: formatDate(preview.periodStart),
                  to: formatDate(preview.periodEnd),
                })}
              </p>
            )}
            {preview.warnings.map((warning, index) => (
              <p
                key={`${warning.code}-${warning.detail ?? ""}-${index}`}
                role="alert"
                className="rounded-field bg-alert-bg p-2.5 text-xs text-alert-ink"
              >
                {t(`bankImport.warning.${warning.code}`, { detail: warning.detail ?? "" })}
              </p>
            ))}

            {rows.length === 0 ? (
              <p className="text-sm text-ink-tertiary">{t("bankImport.noLines")}</p>
            ) : (
              <ul className="flex flex-col divide-y divide-divider-row">
                {rows.map((row, index) => (
                  <ImportRow
                    key={row.line.externalId}
                    index={index}
                    row={row}
                    accounts={activeAccounts}
                    importedAccountId={effectiveAccountId}
                    categories={categoriesByType[row.line.type]}
                    onChange={(patch) => updateRow(index, patch)}
                  />
                ))}
              </ul>
            )}

            {preview.ledgerBalanceCents != null && (
              <p
                className={
                  balanceDiffers
                    ? "rounded-field bg-alert-bg p-2.5 text-xs text-alert-ink"
                    : "text-xs text-ink-tertiary"
                }
                role={balanceDiffers ? "alert" : undefined}
              >
                {t("bankImport.balances", {
                  app: formatCents(balanceAfter),
                  bank: formatCents(preview.ledgerBalanceCents),
                  date: preview.ledgerBalanceDate ? formatDate(preview.ledgerBalanceDate) : "",
                })}
                {balanceDiffers && ` ${t("bankImport.balanceDiffers")}`}
              </p>
            )}

            <DialogFooter className="items-center sm:justify-between">
              <span className="text-sm text-ink-secondary">
                {t("bankImport.selected", { count: selectedRows.length })}
                {": "}
                <span className="tabular-nums font-extrabold text-ink-primary">
                  +{formatCents(income)} / -{formatCents(expense)}
                </span>
                {incomplete && (
                  <span className="block text-xs text-destructive">{t("bankImport.incomplete")}</span>
                )}
              </span>
              <span className="flex gap-2">
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                  {t("common.cancel")}
                </Button>
                <Button
                  type="button"
                  disabled={confirmMutation.isPending || selectedRows.length === 0 || incomplete}
                  onClick={handleImport}
                >
                  {confirmMutation.isPending
                    ? t("common.saving")
                    : t("bankImport.confirm", { count: selectedRows.length })}
                </Button>
              </span>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

interface ImportRowProps {
  index: number;
  row: Row;
  accounts: Account[];
  importedAccountId: string;
  categories: Category[];
  onChange: (patch: Partial<Omit<Row, "line">>) => void;
}

function ImportRow({ index, row, accounts, importedAccountId, categories, onChange }: ImportRowProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const { line } = row;
  const isExpense = line.type === "EXPENSE";

  const modeOptions = [
    { value: "ENTRY", label: t(isExpense ? "bankImport.mode.expense" : "bankImport.mode.income") },
    { value: "TRANSFER", label: t("bankImport.mode.transfer") },
    ...(isExpense ? [{ value: "STATEMENT_PAYMENT", label: t("bankImport.mode.statementPayment") }] : []),
  ];
  const counterpartAccounts = accounts.filter((account) => account.id !== importedAccountId);
  const counterpart = counterpartAccounts.find((account) => account.id === row.counterpartAccountId);
  const showPillar =
    row.mode === "TRANSFER" && isExpense && counterpart !== undefined && isContributionAccount(counterpart);

  return (
    <li className="flex flex-col gap-2 py-3">
      <div className="flex items-start gap-2.5">
        <input
          type="checkbox"
          className="mt-2 size-4 shrink-0"
          checked={row.selected}
          aria-label={line.description}
          onChange={(event) => onChange({ selected: event.target.checked })}
        />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <input
            type="text"
            className="h-8 w-full min-w-0 rounded-md border border-input bg-transparent px-2 text-sm font-bold text-ink-primary"
            aria-label={t("bankImport.descriptionOf", { description: line.description })}
            maxLength={255}
            value={row.description}
            onChange={(event) => onChange({ description: event.target.value })}
          />
          <span className="flex flex-wrap items-center gap-1.5 text-[11px] text-ink-tertiary">
            {formatDate(line.date)}
            {line.duplicate === "EXACT" && <Badge variant="alert">{t("bankImport.duplicate")}</Badge>}
            {line.duplicate === "POSSIBLE" && (
              <Badge variant="alert">{t("bankImport.possibleDuplicate")}</Badge>
            )}
          </span>
        </div>
        <span
          className={`ml-auto shrink-0 pt-1.5 tabular-nums text-sm font-extrabold ${
            isExpense ? "text-ink-primary" : "text-success"
          }`}
        >
          {isExpense ? "-" : "+"}
          {formatCents(line.amountCents)}
        </span>
      </div>

      <div className="flex flex-col gap-2 pl-[26px] sm:flex-row">
        <SelectField
          id={`bank-import-mode-${index}`}
          label={t("bankImport.modeLabel")}
          fieldClassName="min-w-0 sm:w-44 [&>label]:sr-only"
          options={modeOptions}
          value={row.mode}
          onChange={(event) => onChange({ mode: event.target.value as Mode })}
        />
        {row.mode === "ENTRY" && (
          <SelectField
            id={`bank-import-category-${index}`}
            label={t("transactions.category")}
            fieldClassName="min-w-0 flex-1 [&>label]:sr-only"
            placeholder={t("transactions.categoryPlaceholder")}
            options={categories.map((category) => ({ value: category.id, label: category.name }))}
            value={row.categoryId}
            onChange={(event) => onChange({ categoryId: event.target.value })}
          />
        )}
        {row.mode === "TRANSFER" && (
          <SelectField
            id={`bank-import-counterpart-${index}`}
            label={t(isExpense ? "transactions.toAccount" : "transactions.fromAccount")}
            fieldClassName="min-w-0 flex-1 [&>label]:sr-only"
            placeholder={t(isExpense ? "bankImport.toAccountPlaceholder" : "bankImport.fromAccountPlaceholder")}
            options={counterpartAccounts.map((account) => ({ value: account.id, label: account.name }))}
            value={row.counterpartAccountId}
            onChange={(event) => onChange({ counterpartAccountId: event.target.value, pillar: "" })}
          />
        )}
        {showPillar && (
          <SelectField
            id={`bank-import-pillar-${index}`}
            label={t("transactions.contributionPillar")}
            fieldClassName="min-w-0 sm:w-44 [&>label]:sr-only"
            placeholder={t("bankImport.pillarDefault")}
            options={(["GOALS", "FINANCIAL_FREEDOM", "KNOWLEDGE"] as const).map((pillar) => ({
              value: pillar,
              label: t(`pillar.${pillar}`),
            }))}
            value={row.pillar}
            onChange={(event) => onChange({ pillar: event.target.value })}
          />
        )}
        {row.mode === "STATEMENT_PAYMENT" &&
          (line.statementCandidates.length === 0 ? (
            <p className="flex-1 self-center text-xs text-alert-ink">{t("bankImport.noStatement")}</p>
          ) : (
            <SelectField
              id={`bank-import-statement-${index}`}
              label={t("bankImport.statement")}
              fieldClassName="min-w-0 flex-1 [&>label]:sr-only"
              placeholder={t("bankImport.statementPlaceholder")}
              options={line.statementCandidates.map((candidate) => ({
                value: candidate.statementId,
                label: `${candidate.cardName} · ${formatMonth(candidate.referenceMonth)} · ${formatCents(candidate.remainingCents)}`,
              }))}
              value={row.statementId}
              onChange={(event) => onChange({ statementId: event.target.value })}
            />
          ))}
      </div>
    </li>
  );
}
