import { Pillar, type Category } from "@/lib/api/generated/mKFinanceAPI.schemas";

// The six pillars are fixed and non-editable (PLANEJAMENTO §6.4); this is the
// canonical display order used everywhere in the UI.
export const PILLARS: Pillar[] = [
  Pillar.FIXED_COSTS,
  Pillar.COMFORT,
  Pillar.GOALS,
  Pillar.PLEASURES,
  Pillar.FINANCIAL_FREEDOM,
  Pillar.KNOWLEDGE,
];

export interface CategoryNode {
  category: Category;
  children: Category[];
}

function byName(a: Category, b: Category): number {
  return a.name.localeCompare(b.name, "pt-BR");
}

/**
 * Builds the two-level tree the screens render. Subcategories whose parent is
 * not in the same slice (e.g. filtered out by pillar) are promoted to roots so
 * nothing silently disappears from the list.
 */
export function buildCategoryTree(categories: Category[]): CategoryNode[] {
  const ids = new Set(categories.map((category) => category.id));
  const roots = categories.filter((category) => !category.parentId || !ids.has(category.parentId));
  const childrenByParent = new Map<string, Category[]>();

  for (const category of categories) {
    if (category.parentId && ids.has(category.parentId) && category.parentId !== category.id) {
      const siblings = childrenByParent.get(category.parentId) ?? [];
      siblings.push(category);
      childrenByParent.set(category.parentId, siblings);
    }
  }

  return roots
    .sort(byName)
    .map((category) => ({
      category,
      children: (childrenByParent.get(category.id) ?? []).sort(byName),
    }));
}

export function groupExpenseCategoriesByPillar(
  categories: Category[],
): { pillar: Pillar; nodes: CategoryNode[] }[] {
  return PILLARS.map((pillar) => ({
    pillar,
    nodes: buildCategoryTree(categories.filter((category) => category.pillar === pillar)),
  }));
}

/**
 * Categories eligible as a parent: same type, not the edited category itself
 * and not one of its own children (which would create a cycle).
 */
export function eligibleParents(categories: Category[], edited?: Category): Category[] {
  return categories
    .filter((category) => !edited || (category.id !== edited.id && category.parentId !== edited.id))
    .filter((category) => !category.parentId)
    .sort(byName);
}
