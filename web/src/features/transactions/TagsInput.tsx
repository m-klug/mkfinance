import { useMemo, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { X } from "lucide-react";

import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { useListTags } from "@/lib/api/generated/tags/tags";

// Free-text, multi-value tag entry with autocomplete over the workspace's
// existing tags (BE-8 creates whatever name doesn't exist yet, so there is no
// server round-trip on submit — see TransactionRequest.tags in the contract).
// A tag only enters `value` on an explicit commit (Enter, comma, clicking a
// suggestion or leaving the field) — never on every keystroke — so plugging
// this into TransactionsPage's filter never fires a query per key pressed.

interface TagsInputProps {
  id: string;
  label: string;
  workspaceId: string | undefined;
  value: string[];
  onChange: (tags: string[]) => void;
  hint?: string;
  fieldClassName?: string;
}

function sameTag(a: string, b: string): boolean {
  return a.trim().toLowerCase() === b.trim().toLowerCase();
}

export function TagsInput({
  id,
  label,
  workspaceId,
  value,
  onChange,
  hint,
  fieldClassName,
}: TagsInputProps) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState("");
  const [suggestionsOpen, setSuggestionsOpen] = useState(false);

  const { data: existingTags } = useListTags(workspaceId ?? "", {
    query: { enabled: Boolean(workspaceId) },
  });

  const suggestions = useMemo(() => {
    const query = draft.trim().toLowerCase();
    return (existingTags ?? [])
      .filter((tag) => !value.some((selected) => sameTag(selected, tag.name)))
      .filter((tag) => !query || tag.name.toLowerCase().includes(query))
      .slice(0, 8);
  }, [existingTags, draft, value]);

  function commit(raw: string) {
    const name = raw.trim();
    setDraft("");
    if (!name) return;
    if (value.some((selected) => sameTag(selected, name))) return;
    onChange([...value, name]);
  }

  function removeTag(name: string) {
    onChange(value.filter((selected) => !sameTag(selected, name)));
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      commit(draft);
      return;
    }
    if (event.key === "Backspace" && draft === "" && value.length > 0) {
      removeTag(value[value.length - 1]);
    }
  }

  return (
    <div className={cn("flex flex-col gap-2", fieldClassName)}>
      <Label htmlFor={id} className="text-[11px] font-bold tracking-wide text-ink-label uppercase">
        {label}
      </Label>
      <div className="relative">
        <div className="flex min-h-11 flex-wrap items-center gap-1.5 rounded-field border-none bg-field px-2.5 py-1.5">
          {value.map((tag) => (
            <span
              key={tag}
              className="flex items-center gap-1 rounded-pill bg-hero px-3 py-1.5 text-xs font-bold text-hero-foreground"
            >
              <span aria-hidden>#</span>
              {tag}
              <button
                type="button"
                aria-label={t("tags.remove", { name: tag })}
                onClick={() => removeTag(tag)}
                className="text-hero-foreground/60 hover:text-hero-foreground"
              >
                <X className="size-3" />
              </button>
            </span>
          ))}
          <input
            id={id}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={() => setSuggestionsOpen(true)}
            onBlur={() => {
              // Losing focus also commits a half-typed tag, so tabbing away
              // never silently drops what the user just typed.
              commit(draft);
              setSuggestionsOpen(false);
            }}
            placeholder={t("tags.addPlaceholder")}
            autoComplete="off"
            className="h-7 min-w-[8ch] flex-1 border-none bg-transparent text-[13px] font-semibold text-ink-primary outline-none placeholder:text-placeholder"
          />
        </div>
        {suggestionsOpen && suggestions.length > 0 && (
          <ul
            role="listbox"
            aria-label={t("tags.suggestions")}
            className="absolute top-full left-0 z-10 mt-1 max-h-48 w-full overflow-auto rounded-field border-none bg-card p-1 text-sm shadow-[var(--shadow-modal)]"
          >
            {suggestions.map((tag) => (
              <li key={tag.id}>
                <button
                  type="button"
                  role="option"
                  aria-selected={false}
                  className="w-full rounded-[10px] px-2.5 py-1.5 text-left text-ink-primary hover:bg-neutral"
                  // mousedown fires before the input's blur, so the click
                  // registers before commit(draft) on blur can run.
                  onMouseDown={(event) => {
                    event.preventDefault();
                    commit(tag.name);
                  }}
                >
                  <span aria-hidden>#</span>
                  {tag.name}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      {hint && <p className="text-xs text-ink-tertiary">{hint}</p>}
    </div>
  );
}
