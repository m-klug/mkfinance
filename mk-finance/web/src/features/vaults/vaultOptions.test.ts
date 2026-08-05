import { formatCents } from "@/lib/format";
import {
  ContributionPillar,
  VaultGoalStatus,
  type Vault,
  type VaultComposition,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import {
  activeVaults,
  clampPercent,
  DEFAULT_VAULT_COLOR,
  entryAmountClass,
  goalMonthlyLabel,
  goalStatusBadgeClass,
  isUnallocatedOverdrawn,
  selectValueToVaultId,
  vaultBarSegments,
  vaultSelectOptions,
} from "@/features/vaults/vaultOptions";

// Minimal fake t(): returns the key, or "key|{...opts}" when interpolation
// values are passed, so assertions can check both the key chosen and the
// values handed to it without pulling in i18next.
const fakeT = ((key: string, opts?: Record<string, unknown>) =>
  opts ? `${key}|${JSON.stringify(opts)}` : key) as unknown as Parameters<
  typeof goalMonthlyLabel
>[0];

const reserveVault: Vault = {
  id: "vault-reserve",
  accountId: "acc-investment",
  name: "Reserva",
  contributionPillar: ContributionPillar.FINANCIAL_FREEDOM,
  archived: false,
  balanceCents: 600000,
  projectedBalanceCents: 600000,
};

const tripVault: Vault = {
  id: "vault-trip",
  accountId: "acc-investment",
  name: "Viagem 2027",
  contributionPillar: ContributionPillar.GOALS,
  archived: false,
  color: "#f97316",
  balanceCents: 400000,
  projectedBalanceCents: 400000,
};

const archivedVault: Vault = {
  id: "vault-old",
  accountId: "acc-investment",
  name: "Antiga",
  contributionPillar: ContributionPillar.GOALS,
  archived: true,
  balanceCents: 0,
  projectedBalanceCents: 0,
};

function composition(overrides: Partial<VaultComposition> = {}): VaultComposition {
  return {
    accountId: "acc-investment",
    balanceCents: 1050000,
    projectedBalanceCents: 1050000,
    allocatedCents: 1000000,
    unallocatedCents: 50000,
    projectedUnallocatedCents: 50000,
    vaults: [reserveVault, tripVault],
    ...overrides,
  };
}

describe("activeVaults", () => {
  it("drops archived vaults", () => {
    expect(activeVaults([reserveVault, archivedVault])).toEqual([reserveVault]);
  });
});

describe("vaultBarSegments", () => {
  it("builds one segment per vault plus a positive unallocated slice, sized from the account balance", () => {
    const segments = vaultBarSegments(composition(), "Não alocado");

    expect(segments).toHaveLength(3);
    expect(segments[0]).toMatchObject({ key: "vault-reserve", cents: 600000 });
    expect(segments[1]).toMatchObject({ key: "vault-trip", cents: 400000, color: "#f97316" });
    expect(segments[2]).toMatchObject({ key: "unallocated", cents: 50000, label: "Não alocado" });

    // 1_050_000 total: 600k/1050k, 400k/1050k, 50k/1050k.
    expect(segments[0].percent).toBeCloseTo((600000 / 1050000) * 100);
    expect(segments[2].percent).toBeCloseTo((50000 / 1050000) * 100);
  });

  it("falls back to the default color when the vault has none", () => {
    const segments = vaultBarSegments(composition({ vaults: [reserveVault] }), "Não alocado");
    expect(segments[0].color).toBe(DEFAULT_VAULT_COLOR);
  });

  it("excludes a negative unallocated slice from the bar instead of showing a negative width", () => {
    const segments = vaultBarSegments(
      composition({ unallocatedCents: -20000, vaults: [reserveVault] }),
      "Não alocado",
    );
    expect(segments.map((s) => s.key)).toEqual(["vault-reserve"]);
  });

  it("skips archived and zero-balance vaults", () => {
    const emptyVault: Vault = { ...tripVault, id: "vault-empty", balanceCents: 0 };
    const segments = vaultBarSegments(
      composition({ vaults: [reserveVault, emptyVault, archivedVault] }),
      "Não alocado",
    );
    expect(segments.map((s) => s.key)).toEqual(["vault-reserve", "unallocated"]);
  });

  it("never divides by a non-positive total", () => {
    const segments = vaultBarSegments(
      composition({ balanceCents: 0, vaults: [reserveVault] }),
      "Não alocado",
    );
    expect(segments[0].percent).toBe(0);
  });
});

describe("isUnallocatedOverdrawn", () => {
  it("is true only when the unallocated slice is negative (§4.3)", () => {
    expect(isUnallocatedOverdrawn(composition({ unallocatedCents: -1 }))).toBe(true);
    expect(isUnallocatedOverdrawn(composition({ unallocatedCents: 0 }))).toBe(false);
    expect(isUnallocatedOverdrawn(composition({ unallocatedCents: 100 }))).toBe(false);
  });
});

describe("clampPercent", () => {
  it("keeps a goal overshoot from breaking the progress bar's width", () => {
    expect(clampPercent(137.5)).toBe(100);
    expect(clampPercent(-5)).toBe(0);
    expect(clampPercent(42)).toBe(42);
  });
});

describe("vaultSelectOptions", () => {
  it("puts the literal unallocated option first, mapped from value=''", () => {
    const options = vaultSelectOptions(fakeT, [reserveVault, tripVault]);
    expect(options[0]).toEqual({ value: "", label: "vaults.unallocatedOption" });
    expect(options.slice(1)).toEqual([
      { value: "vault-reserve", label: "Reserva" },
      { value: "vault-trip", label: "Viagem 2027" },
    ]);
  });

  it("drops archived vaults and an optionally excluded vault", () => {
    const options = vaultSelectOptions(fakeT, [reserveVault, tripVault, archivedVault], "vault-reserve");
    expect(options.map((option) => option.value)).toEqual(["", "vault-trip"]);
  });
});

describe("selectValueToVaultId", () => {
  it("maps the empty string to null and anything else to itself", () => {
    expect(selectValueToVaultId("")).toBeNull();
    expect(selectValueToVaultId("vault-reserve")).toBe("vault-reserve");
  });
});

describe("goalStatusBadgeClass", () => {
  it("gives each status a distinct, deterministic class", () => {
    const classes = new Set(
      [VaultGoalStatus.IN_PROGRESS, VaultGoalStatus.REACHED, VaultGoalStatus.OVERDUE].map(
        goalStatusBadgeClass,
      ),
    );
    expect(classes.size).toBe(3);
  });
});

describe("entryAmountClass", () => {
  it("colors a negative (outgoing) entry differently from a positive one", () => {
    expect(entryAmountClass(-100)).not.toBe(entryAmountClass(100));
  });
});

describe("goalMonthlyLabel", () => {
  it("celebrates a reached goal regardless of the remaining numbers", () => {
    expect(
      goalMonthlyLabel(fakeT, { status: VaultGoalStatus.REACHED, monthlyNeededCents: 0 }),
    ).toBe("vaults.goal.reached");
  });

  it("says there is no deadline when monthlyNeededCents is null (CT-2 decision 4)", () => {
    expect(
      goalMonthlyLabel(fakeT, { status: VaultGoalStatus.IN_PROGRESS, monthlyNeededCents: null }),
    ).toBe("vaults.goal.noDeadline");
  });

  it("uses the overdue phrasing instead of null when the deadline has passed", () => {
    expect(
      goalMonthlyLabel(fakeT, { status: VaultGoalStatus.OVERDUE, monthlyNeededCents: 62500 }),
    ).toBe(`vaults.goal.monthlyNeededOverdue|${JSON.stringify({ amount: formatCents(62500) })}`);
  });

  it("shows the monthly amount still needed while in progress", () => {
    expect(
      goalMonthlyLabel(fakeT, { status: VaultGoalStatus.IN_PROGRESS, monthlyNeededCents: 62500 }),
    ).toBe(`vaults.goal.monthlyNeeded|${JSON.stringify({ amount: formatCents(62500) })}`);
  });
});
