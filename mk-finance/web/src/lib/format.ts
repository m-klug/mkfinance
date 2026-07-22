// Money is always integer cents end to end (PLANEJAMENTO §5 R1).
// Formatting is centralized here so locale/currency stay consistent.

const brl = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

export function formatCents(cents: number): string {
  return brl.format(cents / 100);
}

export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString("pt-BR");
}

/** "2026-07" → "julho de 2026" */
export function formatMonth(isoMonth: string): string {
  const [year, month] = isoMonth.split("-").map(Number);
  return new Date(year, month - 1, 1).toLocaleDateString("pt-BR", {
    month: "long",
    year: "numeric",
  });
}
