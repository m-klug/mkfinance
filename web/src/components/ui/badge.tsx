import { cn } from "@/lib/utils";

type BadgeVariant = "neutral" | "success" | "alert";

interface BadgeProps {
  variant?: BadgeVariant;
  className?: string;
  children: React.ReactNode;
}

const VARIANT_CLASSES: Record<BadgeVariant, string> = {
  neutral: "bg-neutral text-ink-secondary",
  success: "bg-success-bg text-success-ink",
  alert: "bg-alert-bg text-alert-ink",
};

/** Uppercase status pill (design_handoff_mk_finance/README.md §Badge de status). */
export function Badge({ variant = "neutral", className, children }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-pill px-2.5 py-1 text-[10px] font-extrabold tracking-[0.05em] uppercase",
        VARIANT_CLASSES[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}
