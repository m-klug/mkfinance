import { cn } from "@/lib/utils";

interface SegmentedOption<T extends string> {
  value: T;
  label: string;
}

interface SegmentedControlProps<T extends string> {
  options: SegmentedOption<T>[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
}

/** Pill-track segmented control (design_handoff_mk_finance/README.md §Componentes recorrentes). */
export function SegmentedControl<T extends string>({ options, value, onChange, className }: SegmentedControlProps<T>) {
  return (
    <div
      role="tablist"
      className={cn("flex items-center gap-1 rounded-pill bg-neutral p-1", className)}
    >
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="tab"
          aria-selected={option.value === value}
          onClick={() => onChange(option.value)}
          className={cn(
            "min-w-0 flex-1 truncate rounded-pill px-3.5 py-1.5 text-xs font-bold transition-colors",
            option.value === value
              ? "bg-hero text-hero-foreground"
              : "text-ink-secondary hover:text-ink-primary",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
