import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil, Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import {
  getListCategoriesQueryKey,
  useDeleteCategory,
  useListCategories,
} from "@/lib/api/generated/categories/categories";
import { CategoryType, type Category } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { CategoryFormDialog } from "@/features/categories/CategoryFormDialog";
import {
  buildCategoryTree,
  groupExpenseCategoriesByPillar,
  type CategoryNode,
} from "@/features/categories/categoryTree";

interface CategoryRowProps {
  category: Category;
  depth: number;
  onEdit: (category: Category) => void;
  onDelete: (category: Category) => void;
}

function CategoryRow({ category, depth, onEdit, onDelete }: CategoryRowProps) {
  const { t } = useTranslation();
  return (
    <li
      className="flex items-center justify-between gap-2 rounded-md px-2 py-1.5 hover:bg-muted/50"
      style={{ paddingLeft: `${0.5 + depth * 1.25}rem` }}
    >
      <span className="truncate text-sm">
        {category.name}
        {category.system && (
          <span className="ml-2 rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
            {t("categories.systemBadge")}
          </span>
        )}
      </span>
      {/* System categories (e.g. statement payment) are managed by the API (R2). */}
      {!category.system && (
        <span className="flex shrink-0 gap-1">
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={t("categories.editAction", { name: category.name })}
            onClick={() => onEdit(category)}
          >
            <Pencil className="size-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon-sm"
            aria-label={t("categories.deleteAction", { name: category.name })}
            onClick={() => onDelete(category)}
          >
            <Trash2 className="size-4" />
          </Button>
        </span>
      )}
    </li>
  );
}

function CategoryList({
  nodes,
  onEdit,
  onDelete,
}: {
  nodes: CategoryNode[];
  onEdit: (category: Category) => void;
  onDelete: (category: Category) => void;
}) {
  const { t } = useTranslation();
  if (nodes.length === 0) {
    return <p className="px-2 py-1 text-sm text-muted-foreground">{t("categories.emptyGroup")}</p>;
  }
  return (
    <ul className="flex flex-col">
      {nodes.map(({ category, children }) => (
        <li key={category.id} className="list-none">
          <ul className="flex flex-col">
            <CategoryRow category={category} depth={0} onEdit={onEdit} onDelete={onDelete} />
            {children.map((child) => (
              <CategoryRow
                key={child.id}
                category={child}
                depth={1}
                onEdit={onEdit}
                onDelete={onDelete}
              />
            ))}
          </ul>
        </li>
      ))}
    </ul>
  );
}

export default function CategoriesPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();
  const [activeType, setActiveType] = useState<CategoryType>(CategoryType.EXPENSE);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Category | undefined>();
  const [deleting, setDeleting] = useState<Category | undefined>();

  const { data: categories, isPending } = useListCategories(
    workspaceId ?? "",
    undefined,
    { query: { enabled: Boolean(workspaceId) } },
  );

  const deleteMutation = useDeleteCategory();

  const expenseGroups = useMemo(
    () =>
      groupExpenseCategoriesByPillar(
        (categories ?? []).filter((category) => category.type === CategoryType.EXPENSE),
      ),
    [categories],
  );

  const incomeNodes = useMemo(
    () =>
      buildCategoryTree(
        (categories ?? []).filter((category) => category.type === CategoryType.INCOME),
      ),
    [categories],
  );

  function openCreate() {
    setEditing(undefined);
    setFormOpen(true);
  }

  function openEdit(category: Category) {
    setEditing(category);
    setFormOpen(true);
  }

  function confirmDelete() {
    if (!workspaceId || !deleting) return;
    deleteMutation.mutate(
      { workspaceId, categoryId: deleting.id },
      {
        onSuccess: () => {
          void queryClient.invalidateQueries({ queryKey: getListCategoriesQueryKey(workspaceId) });
          toast.success(t("categories.deleted"));
          setDeleting(undefined);
        },
        onError: (error) => {
          // Typically CATEGORY_IN_USE — the API refuses to orphan transactions.
          toast.error(translateApiError(t, error));
        },
      },
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 p-4 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">{t("nav.categories")}</h1>
        <Button onClick={openCreate}>
          <Plus className="size-4" />
          {t("categories.new")}
        </Button>
      </div>

      {isPending && <p className="text-muted-foreground">{t("common.loading")}</p>}

      {!isPending && (
        <Tabs
          value={activeType}
          onValueChange={(value) => setActiveType(value as CategoryType)}
        >
          <TabsList>
            <TabsTrigger value={CategoryType.EXPENSE}>{t("categoryType.EXPENSE")}</TabsTrigger>
            <TabsTrigger value={CategoryType.INCOME}>{t("categoryType.INCOME")}</TabsTrigger>
          </TabsList>

          <TabsContent value={CategoryType.EXPENSE} className="flex flex-col gap-4">
            {/* Expenses are always grouped by pillar — that is the method's core. */}
            {expenseGroups.map(({ pillar, nodes }) => (
              <Card key={pillar}>
                <CardHeader>
                  <CardTitle className="text-base">{t(`pillar.${pillar}`)}</CardTitle>
                </CardHeader>
                <CardContent>
                  <CategoryList nodes={nodes} onEdit={openEdit} onDelete={setDeleting} />
                </CardContent>
              </Card>
            ))}
          </TabsContent>

          <TabsContent value={CategoryType.INCOME}>
            <Card>
              <CardHeader>
                <CardTitle className="text-base">{t("categoryType.INCOME")}</CardTitle>
              </CardHeader>
              <CardContent>
                <CategoryList nodes={incomeNodes} onEdit={openEdit} onDelete={setDeleting} />
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      )}

      {workspaceId && (
        <CategoryFormDialog
          workspaceId={workspaceId}
          open={formOpen}
          onOpenChange={setFormOpen}
          defaultType={activeType}
          categories={categories ?? []}
          category={editing}
        />
      )}

      <ConfirmDialog
        open={Boolean(deleting)}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title={t("categories.deleteTitle")}
        description={t("categories.deleteDescription", { name: deleting?.name ?? "" })}
        confirmLabel={t("common.delete")}
        destructive
        pending={deleteMutation.isPending}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
