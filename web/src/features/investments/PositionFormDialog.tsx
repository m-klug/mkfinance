import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { MoneyField, SelectField, TextField } from "@/components/form/fields";
import { formatCents, formatDate } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useListAccounts } from "@/lib/api/generated/accounts/accounts";
import { useListVaults } from "@/lib/api/generated/vaults/vaults";
import {
  getListPositionsQueryKey,
  getListUnlinkedContributionsQueryKey,
  getGetInvestmentSummaryQueryKey,
  useCreatePosition,
  useListUnlinkedContributions,
  useUpdatePosition,
} from "@/lib/api/generated/investments/investments";
import type { Position } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { todayIso } from "@/features/transactions/transactionUtils";
import {
  DEFAULT_POSITION_FORM,
  POSITION_INSTRUMENTS,
  formFromPosition,
  positionIndexersFor,
  toCreateRequest,
  toUpdateRequest,
  validatePositionForm,
  withPositionType,
  type PositionFormErrors,
  type PositionFormState,
} from "@/features/investments/positionForm";

interface PositionFormDialogProps {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Present when editing an active position. */
  position?: Position;
}

/**
 * Registers a fixed-income title, or edits an active one. A title has two origins: born from a
 * purchase transfer that no position claims yet (amount, date, account and caixinha come from it), or
 * bought before the app (the user informs the account, the amount and the date, and nothing is moved).
 */
export function PositionFormDialog({ workspaceId, open, onOpenChange, position }: PositionFormDialogProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const mode = position ? "edit" : "create";
  const [form, setForm] = useState<PositionFormState>(DEFAULT_POSITION_FORM);
  const [errors, setErrors] = useState<PositionFormErrors>({});

  useEffect(() => {
    if (open) {
      setForm(position ? formFromPosition(position) : DEFAULT_POSITION_FORM);
      setErrors({});
    }
  }, [open, position]);

  const { data: contributions } = useListUnlinkedContributions(workspaceId, {
    query: { enabled: open && mode === "create" },
  });
  const { data: accounts } = useListAccounts(workspaceId, undefined, {
    query: { enabled: open && mode === "create" && form.origin === "MANUAL" },
  });
  const investmentAccounts = useMemo(
    () => (accounts ?? []).filter((a) => !a.archived && (a.type === "INVESTMENT" || a.type === "SAVINGS")),
    [accounts],
  );

  const chosenContribution = (contributions ?? []).find((c) => c.transactionId === form.contributionId);
  // The account whose caixinhas the user may pick from.
  const accountId = position
    ? position.accountId
    : form.origin === "CONTRIBUTION"
      ? chosenContribution?.accountId
      : form.accountId || undefined;
  const { data: composition } = useListVaults(workspaceId, accountId ?? "", undefined, {
    query: { enabled: open && Boolean(accountId) },
  });
  const vaults = (composition?.vaults ?? []).filter((vault) => !vault.archived);

  const createMutation = useCreatePosition();
  const updateMutation = useUpdatePosition();
  const pending = createMutation.isPending || updateMutation.isPending;

  function patch(partial: Partial<PositionFormState>) {
    setForm((current) => ({ ...current, ...partial }));
    setErrors({});
  }

  function chooseContribution(id: string) {
    const picked = (contributions ?? []).find((c) => c.transactionId === id);
    // The purchase's own caixinha is the natural destination for the yield.
    patch({ contributionId: id, vaultId: picked?.vaultId ?? "" });
  }

  function invalidate() {
    void queryClient.invalidateQueries({ queryKey: getListPositionsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getListUnlinkedContributionsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getGetInvestmentSummaryQueryKey(workspaceId) });
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found = validatePositionForm(form, mode, todayIso(), chosenContribution?.date ?? position?.purchaseDate);
    setErrors(found);
    if (Object.keys(found).length > 0) return;

    const done = {
      onSuccess: () => {
        invalidate();
        toast.success(t(mode === "edit" ? "investments.saved" : "investments.created"));
        onOpenChange(false);
      },
      onError: (error: unknown) => {
        toast.error(translateApiError(t, error));
      },
    };
    if (position) {
      updateMutation.mutate({ workspaceId, positionId: position.id, data: toUpdateRequest(form) }, done);
    } else {
      createMutation.mutate({ workspaceId, data: toCreateRequest(form) }, done);
    }
  }

  const err = (key: keyof PositionFormErrors) => (errors[key] ? t(`investments.errors.${errors[key]}`) : undefined);
  const manualFields = form.origin === "MANUAL";
  const linkedEdit = mode === "edit" && position?.origin === "CONTRIBUTION";

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto rounded-modal">
        <DialogHeader>
          <DialogTitle className="text-[15px] font-extrabold tracking-tight text-ink-primary">
            {t(mode === "edit" ? "investments.editTitle" : "investments.newTitle")}
          </DialogTitle>
          <DialogDescription className="text-ink-tertiary">{t("investments.formSubtitle")}</DialogDescription>
        </DialogHeader>

        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          {mode === "create" && (
            <SegmentedControl
              options={[
                { value: "CONTRIBUTION", label: t("investments.origin.contribution") },
                { value: "MANUAL", label: t("investments.origin.manual") },
              ]}
              value={form.origin}
              onChange={(origin) => patch({ origin, vaultId: "" })}
            />
          )}

          {mode === "create" && form.origin === "CONTRIBUTION" && (
            <SelectField
              id="position-contribution"
              label={t("investments.form.contribution")}
              hint={
                (contributions ?? []).length === 0 ? t("investments.form.noContributions") : t("investments.form.contributionHint")
              }
              placeholder={t("investments.form.contributionPlaceholder")}
              value={form.contributionId}
              onChange={(event) => chooseContribution(event.target.value)}
              error={err("contribution")}
              options={(contributions ?? []).map((c) => ({
                value: c.transactionId,
                label: `${c.accountName} · ${formatCents(c.amountCents)} · ${formatDate(c.date)}`,
              }))}
            />
          )}

          {mode === "create" && manualFields && (
            <>
              <p className="rounded-field bg-field p-3 text-xs text-ink-tertiary">{t("investments.form.manualNotice")}</p>
              <SelectField
                id="position-account"
                label={t("investments.form.account")}
                placeholder={t("investments.form.accountPlaceholder")}
                value={form.accountId}
                onChange={(event) => patch({ accountId: event.target.value, vaultId: "" })}
                error={err("account")}
                options={investmentAccounts.map((a) => ({ value: a.id, label: a.name }))}
              />
            </>
          )}

          {manualFields && (
            <div className="grid gap-4 sm:grid-cols-2">
              <MoneyField
                id="position-principal"
                label={t("investments.form.principal")}
                value={form.principalCents}
                onValueChange={(principalCents) => patch({ principalCents })}
                error={err("principal")}
              />
              <TextField
                id="position-purchase-date"
                label={t("investments.form.purchaseDate")}
                type="date"
                value={form.purchaseDate}
                onChange={(event) => patch({ purchaseDate: event.target.value })}
                error={err("purchaseDate")}
              />
            </div>
          )}

          {linkedEdit && position && (
            <p className="rounded-field bg-field p-3 text-xs text-ink-tertiary">
              {t("investments.form.linkedFacts", {
                amount: formatCents(position.principalCents),
                date: formatDate(position.purchaseDate),
              })}
            </p>
          )}

          <TextField
            id="position-name"
            label={t("investments.form.name")}
            placeholder={t("investments.form.namePlaceholder")}
            maxLength={80}
            autoComplete="off"
            value={form.name}
            onChange={(event) => patch({ name: event.target.value })}
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField
              id="position-type"
              label={t("simulator.form.type")}
              value={form.type}
              onChange={(event) => setForm(withPositionType(form, event.target.value as PositionFormState["type"]))}
              options={POSITION_INSTRUMENTS.map((type) => ({ value: type, label: t(`simulator.instrument.${type}`) }))}
            />
            <SelectField
              id="position-indexer"
              label={t("simulator.form.indexer")}
              value={form.indexer}
              onChange={(event) => patch({ indexer: event.target.value as PositionFormState["indexer"] })}
              options={positionIndexersFor(form.type).map((i) => ({ value: i, label: t(`simulator.indexer.${i}`) }))}
            />
            <TextField
              id="position-rate"
              label={t(`simulator.rateLabel.${form.indexer}`)}
              inputMode="decimal"
              autoComplete="off"
              value={form.rate}
              onChange={(event) => patch({ rate: event.target.value })}
              error={err("rate")}
            />
            <TextField
              id="position-maturity"
              label={t("investments.form.maturity")}
              type="date"
              value={form.maturityDate}
              onChange={(event) => patch({ maturityDate: event.target.value })}
              error={err("maturityDate")}
            />
          </div>

          {accountId && (
            <SelectField
              id="position-vault"
              label={t("investments.form.vault")}
              hint={t("investments.form.vaultHint")}
              placeholder={t("investments.form.noVault")}
              value={form.vaultId}
              onChange={(event) => patch({ vaultId: event.target.value })}
              options={vaults.map((vault) => ({ value: vault.id, label: vault.name }))}
            />
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={pending}>
              {t("common.save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
