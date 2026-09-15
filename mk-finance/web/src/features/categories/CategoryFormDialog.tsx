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
import { SelectField, TextField } from "@/components/form/fields";
import {
  getListCategoriesQueryKey,
  useCreateCategory,
  useUpdateCategory,
} from "@/lib/api/generated/categories/categories";
import {
  CategoryType,
  Pillar,
  type Category,
  type CategoryRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import { translateApiError } from "@/lib/errors";
import { PILLARS, eligibleParents } from "@/features/categories/categoryTree";

interface CategoryFormDialogProps {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Type preselected by the active tab when creating. */
  defaultType: CategoryType;
  categories: Category[];
  category?: Category;
}

interface FormState {
  name: string;
  type: CategoryType;
  pillar: string;
  parentId: string;
}

function toFormState(defaultType: CategoryType, category?: Category): FormState {
  return {
    name: category?.name ?? "",
    type: category?.type ?? defaultType,
    pillar: category?.pillar ?? "",
    parentId: category?.parentId ?? "",
  };
}

export function CategoryFormDialog({
  workspaceId,
  open,
  onOpenChange,
  defaultType,
  categories,
  category,
}: CategoryFormDialogProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FormState>(() => toFormState(defaultType, category));
  const [errors, setErrors] = useState<{ name?: string; pillar?: string }>({});

  useEffect(() => {
    if (open) {
      setForm(toFormState(defaultType, category));
      setErrors({});
    }
  }, [open, defaultType, category]);

  const createMutation = useCreateCategory();
  const updateMutation = useUpdateCategory();
  const isPending = createMutation.isPending || updateMutation.isPending;
  // A pillar is mandatory for EXPENSE and forbidden for INCOME (PLANEJAMENTO §4).
  const requiresPillar = form.type === CategoryType.EXPENSE;

  const parentOptions = useMemo(
    () =>
      eligibleParents(
        categories.filter((candidate) => candidate.type === form.type && !candidate.system),
        category,
      ).map((candidate) => ({ value: candidate.id, label: candidate.name })),
    [categories, form.type, category],
  );

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = form.name.trim();
    const nextErrors: { name?: string; pillar?: string } = {};
    if (!name) nextErrors.name = t("validation.required");
    if (requiresPillar && !form.pillar) nextErrors.pillar = t("validation.required");
    setErrors(nextErrors);
    if (nextErrors.name || nextErrors.pillar) return;

    const data: CategoryRequest = {
      name,
      type: form.type,
      pillar: requiresPillar ? (form.pillar as Pillar) : null,
      parentId: form.parentId || null,
    };

    const options = {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: getListCategoriesQueryKey(workspaceId) });
        toast.success(category ? t("categories.updated") : t("categories.created"));
        onOpenChange(false);
      },
      onError: (error: unknown) => {
        toast.error(translateApiError(t, error));
      },
    };

    if (category) {
      updateMutation.mutate({ workspaceId, categoryId: category.id, data }, options);
    } else {
      createMutation.mutate({ workspaceId, data }, options);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {category ? t("categories.editTitle") : t("categories.newTitle")}
          </DialogTitle>
          <DialogDescription>{t("categories.formSubtitle")}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <TextField
            id="category-name"
            label={t("categories.name")}
            maxLength={80}
            autoComplete="off"
            value={form.name}
            error={errors.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <SelectField
            id="category-type"
            label={t("categories.type")}
            options={[
              { value: CategoryType.EXPENSE, label: t("categoryType.EXPENSE") },
              { value: CategoryType.INCOME, label: t("categoryType.INCOME") },
            ]}
            value={form.type}
            onChange={(event) =>
              setForm({
                ...form,
                type: event.target.value as CategoryType,
                // Switching type invalidates both the pillar and the parent.
                pillar: "",
                parentId: "",
              })
            }
          />
          {requiresPillar && (
            <SelectField
              id="category-pillar"
              label={t("categories.pillar")}
              placeholder={t("categories.pillarPlaceholder")}
              options={PILLARS.map((pillar) => ({ value: pillar, label: t(`pillar.${pillar}`) }))}
              value={form.pillar}
              error={errors.pillar}
              onChange={(event) => setForm({ ...form, pillar: event.target.value })}
            />
          )}
          <SelectField
            id="category-parent"
            label={t("categories.parent")}
            placeholder={t("categories.noParent")}
            options={parentOptions}
            value={form.parentId}
            onChange={(event) => setForm({ ...form, parentId: event.target.value })}
          />
          <p className="rounded-field bg-field px-3 py-2.5 text-xs text-ink-tertiary">
            {t("categories.systemNote")}
          </p>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? t("common.saving") : t("common.save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
