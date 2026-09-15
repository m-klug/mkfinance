import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil, Plus, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { cn } from "@/lib/utils";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import {
  getListCategoriesQueryKey,
  useDeleteCategory,
  useListCategories,
} from "@/lib/api/generated/categories/categories";
import { CategoryType, type Category } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { CategoryFormDialog } from "@/features/categories/CategoryFormDialog";
import { PILLAR_COLORS } from "@/features/dashboard/dashboardFormat";
import {
  buildCategoryTree,
  groupExpenseCategoriesByPillar,
  type CategoryNode,
} from "@/features/categories/categoryTree";

// Pill-track tab, matching the segmented control look (design_handoff_mk_finance/README.md).
const TABS_LIST_CLASS = "h-auto w-fit gap-1 rounded-pill bg-neutral p-1";
const TAB_TRIGGER_CLASS = cn(
  "rounded-pill border-none px-4 py-1.5 text-xs font-bold text-ink-secondary shadow-none",
  "data-[state=active]:bg-hero data-[state=active]:text-hero-foreground data-[state=active]:shadow-none",
  "hover:text-ink-primary data-[state=active]:hover:text-hero-foreground",
);

interface CategoryListProps {
  nodes: CategoryNode[];
  onEdit: (category: Category) => void;
  onDelete: (category: Category) => void;
}

function CategoryList({ nodes, onEdit, onDelete }: CategoryListProps) {
  const { t } = useTranslation();
  if (nodes.length === 0) {
    return <p className="text-sm text-ink-tertiary">{t("categories.emptyGroup")}</p>;
  }
  return (
    <ul className="flex flex-col divide-y divide-divider-row">
      {nodes.map(({ category, children }) => (
        <li key={category.id} className="flex flex-col gap-2 py-3 first:pt-0 last:pb-0">
          <div className="flex items-center justify-between gap-2">
            <span className="flex min-w-0 items-center gap-2 truncate text-sm font-bold text-ink-primary">
              <span className="truncate">{category.name}</span>
              {category.system && <Badge variant="neutral">{t("categories.systemBadge")}</Badge>}
            </span>
            {/* System categories (e.g. statement payment) are managed by the API (R2). */}
            {!category.system && (
              <span className="flex shrink-0 gap-1">
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="rounded-full text-ink-tertiary hover:bg-neutral hover:text-ink-primary"
                  aria-label={t("categories.editAction", { name: category.name })}
                  onClick={() => onEdit(category)}
                >
                  <Pencil className="size-3.5" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="rounded-full text-ink-tertiary hover:bg-neutral hover:text-ink-primary"
                  aria-label={t("categories.deleteAction", { name: category.name })}
                  onClick={() => onDelete(category)}
                >
                  <Trash2 className="size-3.5" />
                </Button>
              </span>
            )}
          </div>

          {children.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {children.map((child) => (
                <span
                  key={child.id}
                  className="inline-flex items-center gap-1.5 rounded-pill bg-field py-1 pl-2.5 pr-1.5 text-xs font-semibold text-ink-secondary"
                >
                  {child.name}
                  {child.system && (
                    <Badge variant="neutral" className="px-1.5 py-0.5 text-[9px]">
                      {t("categories.systemBadge")}
                    </Badge>
                  )}
                  {!child.system && (
                    <span className="flex items-center gap-0.5">
                      <button
                        type="button"
                        aria-label={t("categories.editAction", { name: child.name })}
                        onClick={() => onEdit(child)}
                        className="rounded-full p-0.5 text-ink-tertiary hover:bg-card hover:text-ink-primary"
                      >
                        <Pencil className="size-3" />
                      </button>
                      <button
                        type="button"
                        aria-label={t("categories.deleteAction", { name: child.name })}
                        onClick={() => onDelete(child)}
                        className="rounded-full p-0.5 text-ink-tertiary hover:bg-card hover:text-ink-primary"
                      >
                        <Trash2 className="size-3" />
                      </button>
                    </span>
                  )}
                </span>
              ))}
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}

/** Total categories (roots + subcategories) rendered inside a pillar group. */
function countCategories(nodes: CategoryNode[]): number {
  return nodes.reduce((total, node) => total + 1 + node.children.length, 0);
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
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-ink-label">
            {t("nav.categories")}
          </p>
          <h1 className="text-[26px] font-extrabold tracking-[-0.025em] text-ink-primary">
            {t("nav.categories")}
          </h1>
        </div>
        <Button onClick={openCreate}>
          <Plus className="size-4" />
          {t("categories.new")}
        </Button>
      </div>

      {isPending && <p className="text-ink-tertiary">{t("common.loading")}</p>}

      {!isPending && (
        <Tabs
          value={activeType}
          onValueChange={(value) => setActiveType(value as CategoryType)}
        >
          <TabsList className={TABS_LIST_CLASS}>
            <TabsTrigger value={CategoryType.EXPENSE} className={TAB_TRIGGER_CLASS}>
              {t("categoryType.EXPENSE")}
            </TabsTrigger>
            <TabsTrigger value={CategoryType.INCOME} className={TAB_TRIGGER_CLASS}>
              {t("categoryType.INCOME")}
            </TabsTrigger>
          </TabsList>

          <TabsContent value={CategoryType.EXPENSE} className="flex flex-col gap-4 pt-4">
            {/* Expenses are always grouped by pillar — that is the method's core. */}
            {expenseGroups.map(({ pillar, nodes }) => (
              <Card key={pillar} className="gap-3 rounded-card border border-divider-card bg-card p-5">
                <CardHeader className="grid-rows-none flex flex-row items-center justify-between gap-2">
                  <div className="flex items-center gap-2.5">
                    <span
                      className="size-2.5 shrink-0 rounded-[4px]"
                      style={{ backgroundColor: PILLAR_COLORS[pillar] }}
                      aria-hidden
                    />
                    <CardTitle className="text-[15px] font-extrabold tracking-[-0.015em] text-ink-primary">
                      {t(`pillar.${pillar}`)}
                    </CardTitle>
                  </div>
                  <Badge variant="neutral">
                    {t("categories.pillarCount", { count: countCategories(nodes) })}
                  </Badge>
                </CardHeader>
                <CardContent>
                  <CategoryList nodes={nodes} onEdit={openEdit} onDelete={setDeleting} />
                </CardContent>
              </Card>
            ))}
          </TabsContent>

          <TabsContent value={CategoryType.INCOME} className="pt-4">
            <Card className="gap-3 rounded-card border border-divider-card bg-card p-5">
              <CardHeader>
                <CardTitle className="text-[15px] font-extrabold tracking-[-0.015em] text-ink-primary">
                  {t("categoryType.INCOME")}
                </CardTitle>
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
