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
import { useFormatCents } from "@/hooks/useFormatCents";
import { formatDate, formatMonth } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import {
  useCreateCardPurchasesBatch,
  usePreviewStatementImport,
} from "@/lib/api/generated/cards/cards";
import type {
  Category,
  StatementImportPreview,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

interface StatementImportDialogProps {
  workspaceId: string;
  cardId: string;
  categories: Category[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Called with the statement month the purchases were charged to. */
  onImported: (statementMonth: string) => void;
}

interface Row {
  line: StatementImportPreview["lines"][number];
  selected: boolean;
  categoryId: string;
  /** Installment of a hand-typed purchase this line is attached to, or {@link CREATE_NEW}. */
  link: string;
}

const CREATE_NEW = "new";

function toRows(preview: StatementImportPreview): Row[] {
  return preview.lines.map((line) => ({
    line,
    // Already-imported lines start unchecked, and so do ambiguous ones (the user must pick which
    // entry of theirs it is, or none); credits can't be imported at all (the card has no concept of one).
    selected: line.kind === "EXPENSE" && line.status !== "ALREADY_IMPORTED" && line.status !== "AMBIGUOUS",
    categoryId: line.suggestedCategoryId ?? "",
    // A single match is linked by default: that is the whole point of recognizing it.
    link: line.status === "MATCH" ? line.candidates[0].installmentId : CREATE_NEW,
  }));
}

export function StatementImportDialog({
  workspaceId,
  cardId,
  categories,
  open,
  onOpenChange,
  onImported,
}: StatementImportDialogProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const [file, setFile] = useState<File | undefined>();
  const [password, setPassword] = useState("");
  const [fileError, setFileError] = useState<string | undefined>();
  const [preview, setPreview] = useState<StatementImportPreview | undefined>();
  const [rows, setRows] = useState<Row[]>([]);

  const previewMutation = usePreviewStatementImport();
  const batchMutation = useCreateCardPurchasesBatch();

  // The password and the file never outlive the dialog.
  useEffect(() => {
    if (open) return;
    setFile(undefined);
    setPassword("");
    setFileError(undefined);
    setPreview(undefined);
    setRows([]);
  }, [open]);

  const expenseCategories = useMemo(
    () => categories.filter((category) => category.type === "EXPENSE" && !category.system),
    [categories],
  );

  const selectedRows = rows.filter((row) => row.selected);
  const selectedTotal = selectedRows.reduce((sum, row) => sum + row.line.amountCents, 0);
  const missingCategory = selectedRows.some((row) => row.link === CREATE_NEW && !row.categoryId);

  function handleRead(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) {
      setFileError(t("validation.required"));
      return;
    }
    setFileError(undefined);
    previewMutation.mutate(
      { workspaceId, cardId, data: { file, password } },
      {
        onSuccess: (result) => {
          setPassword("");
          setPreview(result);
          setRows(toRows(result));
        },
        onError: (error) => toast.error(translateApiError(t, error)),
      },
    );
  }

  function updateRow(index: number, patch: Partial<Pick<Row, "selected" | "categoryId" | "link">>) {
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  function handleImport() {
    if (!preview || selectedRows.length === 0 || missingCategory) return;
    batchMutation.mutate(
      {
        workspaceId,
        cardId,
        data: {
          statementMonth: preview.statementMonth,
          items: selectedRows.map((row) => ({
            categoryId: row.link === CREATE_NEW ? row.categoryId : undefined,
            description: row.line.description,
            totalAmountCents: row.line.amountCents,
            purchaseDate: row.line.date,
            externalId: row.line.externalId,
            linkInstallmentId: row.link === CREATE_NEW ? undefined : row.link,
          })),
        },
      },
      {
        onSuccess: () => {
          toast.success(t("statement.import.done", { count: selectedRows.length }));
          onImported(preview.statementMonth);
          onOpenChange(false);
        },
        onError: (error) => toast.error(translateApiError(t, error)),
      },
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto rounded-modal sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="text-[15px] font-extrabold tracking-tight text-ink-primary">
            {t("statement.import.title")}
          </DialogTitle>
          <DialogDescription className="text-ink-tertiary">
            {preview
              ? t("statement.import.reviewSubtitle", {
                  month: formatMonth(preview.statementMonth),
                  date: formatDate(preview.dueDate),
                })
              : t("statement.import.uploadSubtitle")}
          </DialogDescription>
        </DialogHeader>

        {!preview && (
          <form className="flex flex-col gap-4" onSubmit={handleRead} noValidate>
            <TextField
              id="import-file"
              label={t("statement.import.file")}
              type="file"
              accept="application/pdf,.pdf"
              error={fileError}
              onChange={(event) => setFile(event.target.files?.[0])}
            />
            <TextField
              id="import-password"
              label={t("statement.import.password")}
              hint={t("statement.import.passwordHint")}
              type="password"
              autoComplete="off"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" disabled={previewMutation.isPending}>
                {previewMutation.isPending
                  ? t("statement.import.reading")
                  : t("statement.import.read")}
              </Button>
            </DialogFooter>
          </form>
        )}

        {preview && (
          <div className="flex flex-col gap-3">
            {preview.warnings
              .filter((warning) => warning.code !== "PAYMENT_IGNORED")
              .map((warning, index) => (
                <p
                  key={`${warning.code}-${warning.detail ?? ""}-${index}`}
                  role="alert"
                  className="rounded-field bg-alert-bg p-2.5 text-xs text-alert-ink"
                >
                  {t(`statement.import.warning.${warning.code}`, { card: warning.detail ?? "" })}
                </p>
              ))}

            {rows.length === 0 ? (
              <p className="text-sm text-ink-tertiary">{t("statement.import.noLines")}</p>
            ) : (
              <ul className="flex flex-col divide-y divide-divider-row">
                {rows.map((row, index) => {
                  const importable = row.line.kind === "EXPENSE";
                  return (
                    <li
                      key={`${row.line.cardLast4}-${row.line.date}-${row.line.description}-${index}`}
                      className="flex flex-col gap-2 py-2.5 sm:flex-row sm:items-center"
                    >
                      <label className="flex min-w-0 flex-1 items-start gap-2.5">
                        <input
                          type="checkbox"
                          className="mt-1 size-4 shrink-0"
                          checked={row.selected}
                          disabled={!importable}
                          aria-label={row.line.description}
                          onChange={(event) => updateRow(index, { selected: event.target.checked })}
                        />
                        <span className="flex min-w-0 flex-col">
                          <span className="truncate text-sm font-bold text-ink-primary">
                            {row.line.description}
                          </span>
                          <span className="flex flex-wrap items-center gap-1.5 text-[11px] text-ink-tertiary">
                            {formatDate(row.line.date)} · ····{row.line.cardLast4}
                            {row.line.status === "ALREADY_IMPORTED" && (
                              <Badge variant="alert">{t("statement.import.duplicate")}</Badge>
                            )}
                            {row.line.status === "MATCH" && (
                              <Badge variant="neutral">{t("statement.import.match")}</Badge>
                            )}
                            {row.line.status === "AMBIGUOUS" && (
                              <Badge variant="alert">{t("statement.import.ambiguous")}</Badge>
                            )}
                            {row.line.kind === "CREDIT" && (
                              <Badge variant="neutral">{t("statement.import.credit")}</Badge>
                            )}
                          </span>
                        </span>
                      </label>
                      <div className="flex items-center gap-3 sm:w-72">
                        {importable && row.line.candidates.length > 0 && (
                          <SelectField
                            id={`import-link-${index}`}
                            label={t("statement.import.linkTo")}
                            fieldClassName="min-w-0 flex-1 [&>label]:sr-only"
                            options={[
                              ...row.line.candidates.map((candidate) => ({
                                value: candidate.installmentId,
                                label: t("statement.import.candidate", {
                                  description: candidate.description,
                                  date: formatDate(candidate.purchaseDate),
                                }),
                              })),
                              { value: CREATE_NEW, label: t("statement.import.createNew") },
                            ]}
                            value={row.link}
                            disabled={!row.selected}
                            onChange={(event) => updateRow(index, { link: event.target.value })}
                          />
                        )}
                        {importable && row.link === CREATE_NEW && (
                          <SelectField
                            id={`import-category-${index}`}
                            label={t("statement.itemCategory")}
                            fieldClassName="min-w-0 flex-1 [&>label]:sr-only"
                            placeholder={t("transactions.categoryPlaceholder")}
                            options={expenseCategories.map((category) => ({
                              value: category.id,
                              label: category.name,
                            }))}
                            value={row.categoryId}
                            disabled={!row.selected}
                            onChange={(event) => updateRow(index, { categoryId: event.target.value })}
                          />
                        )}
                        <span className="ml-auto tabular-nums text-sm font-extrabold text-ink-primary">
                          {formatCents(row.line.amountCents)}
                        </span>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}

            <DialogFooter className="items-center sm:justify-between">
              <span className="text-sm text-ink-secondary">
                {t("statement.import.selected", { count: selectedRows.length })}:{" "}
                <span className="tabular-nums font-extrabold text-ink-primary">
                  {formatCents(selectedTotal)}
                </span>
                {missingCategory && (
                  <span className="block text-xs text-destructive">
                    {t("statement.import.missingCategory")}
                  </span>
                )}
              </span>
              <span className="flex gap-2">
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                  {t("common.cancel")}
                </Button>
                <Button
                  type="button"
                  disabled={
                    batchMutation.isPending || selectedRows.length === 0 || missingCategory
                  }
                  onClick={handleImport}
                >
                  {batchMutation.isPending
                    ? t("common.saving")
                    : t("statement.import.confirm", { count: selectedRows.length })}
                </Button>
              </span>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
