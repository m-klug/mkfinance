import { Pillar, type PillarAllocation } from "@/lib/api/generated/mKFinanceAPI.schemas";

// The six fixed pillars in the order PLANEJAMENTO §6.4 lists the MVP defaults
// (30/10/20/10/25/5) — kept local to this feature rather than importing the
// dashboard's PILLAR_ORDER, since features/settings owns nothing outside
// features/settings/**.
export const PILLAR_ORDER: Pillar[] = [
  Pillar.FIXED_COSTS,
  Pillar.COMFORT,
  Pillar.GOALS,
  Pillar.PLEASURES,
  Pillar.FINANCIAL_FREEDOM,
  Pillar.KNOWLEDGE,
];

export type PillarPercentForm = Record<Pillar, number>;

const ZERO_FORM: PillarPercentForm = PILLAR_ORDER.reduce((form, pillar) => {
  form[pillar] = 0;
  return form;
}, {} as PillarPercentForm);

/** Builds the editable form state from the targets currently in force. */
export function toPillarPercentForm(allocations: PillarAllocation[]): PillarPercentForm {
  const form = { ...ZERO_FORM };
  for (const allocation of allocations) {
    form[allocation.pillar] = allocation.targetPercent;
  }
  return form;
}

export function sumPercents(form: PillarPercentForm): number {
  return PILLAR_ORDER.reduce((total, pillar) => total + (form[pillar] ?? 0), 0);
}

// The contract rejects a subset (PillarAllocationsRequest requires exactly
// six items) — always send all six, never just the ones the user touched.
export function toAllocationsRequest(form: PillarPercentForm) {
  return PILLAR_ORDER.map((pillar) => ({ pillar, targetPercent: form[pillar] ?? 0 }));
}

/** Earliest `effectiveFrom` among the loaded targets, for the "in force since" hint. */
export function earliestEffectiveFrom(allocations: PillarAllocation[]): string | undefined {
  return allocations
    .map((allocation) => allocation.effectiveFrom)
    .filter((date): date is string => Boolean(date))
    .sort()[0];
}
