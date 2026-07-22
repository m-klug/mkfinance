import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { QuickTransactionDialog } from "@/features/transactions/QuickTransactionDialog";

/**
 * Quick entry must be reachable from every screen (PLANEJAMENTO §6.3), so the
 * app shell mounts this single component in its header and owns nothing else
 * about it — all the state lives here.
 */
export function QuickTransactionButton() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button size="sm" onClick={() => setOpen(true)}>
        <Plus className="size-4" />
        {t("quickEntry.open")}
      </Button>
      <QuickTransactionDialog open={open} onOpenChange={setOpen} />
    </>
  );
}
