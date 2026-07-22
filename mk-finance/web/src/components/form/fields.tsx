import type { ComponentProps, ReactNode } from "react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { formatCents } from "@/lib/format";
import { parseAmountToCents } from "@/components/form/money";

// Thin, dependency-free form primitives shared by the accounts, categories and
// transactions screens. Deliberately built on the native <select> instead of a
// listbox widget: on mobile it opens the OS picker (this app is used mostly on
// a phone), it is keyboard/screen-reader correct for free, and it keeps the
// forms trivially testable with userEvent.selectOptions.

interface FieldShellProps {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  className?: string;
  children: ReactNode;
}

function FieldShell({ id, label, error, hint, className, children }: FieldShellProps) {
  return (
    <div className={cn("flex flex-col gap-2", className)}>
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && !error && <p className="text-xs text-muted-foreground">{hint}</p>}
      {error && (
        <p id={`${id}-error`} role="alert" className="text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

export interface TextFieldProps extends Omit<ComponentProps<"input">, "id"> {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  fieldClassName?: string;
}

export function TextField({
  id,
  label,
  error,
  hint,
  fieldClassName,
  ...props
}: TextFieldProps) {
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} className={fieldClassName}>
      <Input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        {...props}
      />
    </FieldShell>
  );
}

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectFieldProps extends Omit<ComponentProps<"select">, "id" | "children"> {
  id: string;
  label: string;
  options: SelectOption[];
  placeholder?: string;
  error?: string;
  hint?: string;
  fieldClassName?: string;
}

export function SelectField({
  id,
  label,
  options,
  placeholder,
  error,
  hint,
  fieldClassName,
  className,
  ...props
}: SelectFieldProps) {
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} className={fieldClassName}>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        className={cn(
          "h-9 w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-xs outline-none md:text-sm dark:bg-input/30",
          "focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50",
          "aria-invalid:border-destructive aria-invalid:ring-destructive/20",
          "disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        {...props}
      >
        {placeholder !== undefined && <option value="">{placeholder}</option>}
        {options.map((option) => (
          <option key={option.value} value={option.value} disabled={option.disabled}>
            {option.label}
          </option>
        ))}
      </select>
    </FieldShell>
  );
}

export interface MoneyFieldProps {
  id: string;
  label: string;
  /** Value in integer cents. */
  value: number;
  onValueChange: (cents: number) => void;
  error?: string;
  hint?: string;
  autoFocus?: boolean;
  fieldClassName?: string;
}

export function MoneyField({
  id,
  label,
  value,
  onValueChange,
  error,
  hint,
  autoFocus,
  fieldClassName,
}: MoneyFieldProps) {
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} className={fieldClassName}>
      <Input
        id={id}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        autoFocus={autoFocus}
        value={value === 0 ? "" : formatCents(value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onValueChange(parseAmountToCents(event.target.value))}
      />
    </FieldShell>
  );
}
