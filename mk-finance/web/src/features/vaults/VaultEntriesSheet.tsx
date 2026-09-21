import { useTranslation } from "react-i18next";

import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { formatDate } from "@/lib/format";
import { useFormatCents } from "@/hooks/useFormatCents";
import { useListVaultEntries } from "@/lib/api/generated/vaults/vaults";
import type { Vault } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { entryAmountClass } from "@/features/vaults/vaultOptions";

interface VaultEntriesSheetProps {
  workspaceId: string;
  /** Absent while no vault's entries are being viewed — the dialog stays closed. */
  vault?: Vault;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * The vault's own ledger (§4.1), most recent first, exactly as the API
 * returns it. amountCents is signed here — unlike everywhere else in the API
 * — so it is colored instead of re-derived from a type (CT-2 decision 3).
 */
export function VaultEntriesSheet({
  workspaceId,
  vault,
  open,
  onOpenChange,
}: VaultEntriesSheetProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const enabled = open && Boolean(workspaceId) && Boolean(vault?.id);

  const { data: entries, isPending } = useListVaultEntries(workspaceId, vault?.id ?? "", undefined, {
    query: { enabled },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-modal sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="text-[15px] font-extrabold tracking-tight text-ink-primary">
            {t("vaults.entriesTitle", { name: vault?.name ?? "" })}
          </DialogTitle>
          <DialogDescription className="text-ink-tertiary">
            {t("vaults.entriesSubtitle")}
          </DialogDescription>
        </DialogHeader>

        {isPending && <p className="text-sm text-ink-tertiary">{t("common.loading")}</p>}
        {!isPending && (entries?.length ?? 0) === 0 && (
          <p className="text-sm text-ink-tertiary">{t("vaults.entriesEmpty")}</p>
        )}
        {!isPending && (entries?.length ?? 0) > 0 && (
          <Table>
            <TableHeader>
              <TableRow className="border-divider-card hover:bg-transparent">
                <TableHead className="text-[11px] font-bold text-ink-label">
                  {t("vaults.entryDate")}
                </TableHead>
                <TableHead className="text-[11px] font-bold text-ink-label">
                  {t("vaults.entryDescription")}
                </TableHead>
                <TableHead className="text-[11px] font-bold text-ink-label">
                  {t("vaults.entrySource")}
                </TableHead>
                <TableHead className="text-[11px] font-bold text-ink-label">
                  {t("vaults.entryStatus")}
                </TableHead>
                <TableHead className="text-right text-[11px] font-bold text-ink-label">
                  {t("vaults.entryAmount")}
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(entries ?? []).map((entry) => (
                <TableRow key={entry.id} className="border-divider-row">
                  <TableCell className="tabular-nums text-ink-tertiary">
                    {formatDate(entry.date)}
                  </TableCell>
                  <TableCell className="font-bold text-ink-primary">{entry.description}</TableCell>
                  <TableCell className="text-ink-secondary">
                    {t(`vaults.entrySourceLabel.${entry.source}`)}
                  </TableCell>
                  <TableCell>
                    <Badge variant={entry.status === "PENDING" ? "alert" : "neutral"}>
                      {t(`transactionStatus.${entry.status}`)}
                    </Badge>
                  </TableCell>
                  <TableCell
                    className={cn(
                      "text-right tabular-nums font-extrabold",
                      entryAmountClass(entry.amountCents),
                    )}
                  >
                    {formatCents(entry.amountCents)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
    </Dialog>
  );
}
