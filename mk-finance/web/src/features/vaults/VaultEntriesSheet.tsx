import { useTranslation } from "react-i18next";

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
import { formatCents, formatDate } from "@/lib/format";
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
  const enabled = open && Boolean(workspaceId) && Boolean(vault?.id);

  const { data: entries, isPending } = useListVaultEntries(workspaceId, vault?.id ?? "", undefined, {
    query: { enabled },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t("vaults.entriesTitle", { name: vault?.name ?? "" })}</DialogTitle>
          <DialogDescription>{t("vaults.entriesSubtitle")}</DialogDescription>
        </DialogHeader>

        {isPending && <p className="text-muted-foreground">{t("common.loading")}</p>}
        {!isPending && (entries?.length ?? 0) === 0 && (
          <p className="text-muted-foreground">{t("vaults.entriesEmpty")}</p>
        )}
        {!isPending && (entries?.length ?? 0) > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("vaults.entryDate")}</TableHead>
                <TableHead>{t("vaults.entryDescription")}</TableHead>
                <TableHead>{t("vaults.entrySource")}</TableHead>
                <TableHead>{t("vaults.entryStatus")}</TableHead>
                <TableHead className="text-right">{t("vaults.entryAmount")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(entries ?? []).map((entry) => (
                <TableRow key={entry.id}>
                  <TableCell className="tabular-nums">{formatDate(entry.date)}</TableCell>
                  <TableCell>{entry.description}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {t(`vaults.entrySourceLabel.${entry.source}`)}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {t(`transactionStatus.${entry.status}`)}
                  </TableCell>
                  <TableCell
                    className={cn("text-right tabular-nums font-medium", entryAmountClass(entry.amountCents))}
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
