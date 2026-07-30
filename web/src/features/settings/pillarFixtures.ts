import { http, HttpResponse } from "msw";

import { Pillar, type PillarAllocation } from "@/lib/api/generated/mKFinanceAPI.schemas";

// The MVP defaults from PLANEJAMENTO §6.4 (30/10/20/10/25/5), in force since
// the seed date — a small, stable fixture for the FE-5 tests (the orval mocks
// are random faker data with a 1s delay and useless for assertions).
export const defaultPillarAllocations: PillarAllocation[] = [
  { pillar: Pillar.FIXED_COSTS, targetPercent: 30, effectiveFrom: "2026-01-01" },
  { pillar: Pillar.COMFORT, targetPercent: 10, effectiveFrom: "2026-01-01" },
  { pillar: Pillar.GOALS, targetPercent: 20, effectiveFrom: "2026-01-01" },
  { pillar: Pillar.PLEASURES, targetPercent: 10, effectiveFrom: "2026-01-01" },
  { pillar: Pillar.FINANCIAL_FREEDOM, targetPercent: 25, effectiveFrom: "2026-01-01" },
  { pillar: Pillar.KNOWLEDGE, targetPercent: 5, effectiveFrom: "2026-01-01" },
];

interface PillarAllocationHandlerOptions {
  allocations?: PillarAllocation[];
}

/** GET handler for the targets currently in force. Pass to `server.use(...)`. */
export function pillarAllocationReadHandler({
  allocations = defaultPillarAllocations,
}: PillarAllocationHandlerOptions = {}) {
  return http.get("*/workspaces/:workspaceId/pillar-allocations", () =>
    HttpResponse.json(allocations),
  );
}
