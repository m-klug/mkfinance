import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Plus } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { QuickTransactionDialog } from "@/features/transactions/QuickTransactionDialog";

interface QuickTransactionButtonProps {
  /** "button" is the desktop topbar pill; "fab" is the mobile tab bar's raised mint circle (design_handoff_mk_finance/README.md §Mobile). */
  variant?: "button" | "fab";
}

/**
 * Quick entry must be reachable from every screen (PLANEJAMENTO §6.3), so the
 * app shell mounts this single component (desktop topbar, mobile tab bar)
 * and owns nothing else about it — all the state lives here.
 */
export function QuickTransactionButton({ variant = "button" }: QuickTransactionButtonProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  return (
    <>
      {variant === "fab" ? (
        <button
          type="button"
          aria-label={t("quickEntry.open")}
          onClick={() => setOpen(true)}
          className={cn(
            "-mt-6 flex size-14 shrink-0 items-center justify-center rounded-[20px] bg-mint text-mint-ink shadow-[0_10px_22px_rgba(47,191,143,0.4)]",
          )}
        >
          <Plus className="size-6" strokeWidth={2.5} />
        </button>
      ) : (
        <Button size="sm" onClick={() => setOpen(true)}>
          <Plus className="size-4" />
          {t("quickEntry.open")}
        </Button>
      )}
      <QuickTransactionDialog open={open} onOpenChange={setOpen} />
    </>
  );
}
