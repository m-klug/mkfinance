import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

import { PILLAR_ORDER } from "@/features/dashboard/dashboardData";
import { PILLAR_COLORS } from "@/features/dashboard/dashboardFormat";
import { Pillar } from "@/lib/api/generated/mKFinanceAPI.schemas";

interface AuthShellProps {
  children: ReactNode;
}

// Default six-pillar split (PLANEJAMENTO §6.6) — shown as a static illustration
// on the auth screen, before any workspace/target is loaded. Purely decorative;
// real targets always come from the API once the user is in.
const DEFAULT_TARGETS: Record<Pillar, number> = {
  [Pillar.FIXED_COSTS]: 30,
  [Pillar.COMFORT]: 10,
  [Pillar.GOALS]: 20,
  [Pillar.PLEASURES]: 10,
  [Pillar.FINANCIAL_FREEDOM]: 25,
  [Pillar.KNOWLEDGE]: 5,
};
const AXIS_MAX = 50;

/**
 * Dark shell shared by Login/Register (design_handoff_mk_finance/README.md
 * screen 13): left value-prop with the 6 pillar bars, right the auth card.
 */
export function AuthShell({ children }: AuthShellProps) {
  const { t } = useTranslation();

  return (
    <div className="flex min-h-screen items-center justify-center bg-page-bg p-4">
      <div className="flex w-full max-w-[1180px] flex-col overflow-hidden rounded-shell bg-hero shadow-[var(--shadow-shell)] lg:min-h-[640px] lg:flex-row">
        <div className="flex flex-1 flex-col justify-between gap-8 p-8 text-hero-foreground sm:p-10">
          <div className="flex items-center gap-2.5">
            <span className="flex size-8 items-center justify-center rounded-[11px] bg-mint text-sm font-extrabold text-mint-ink">
              M
            </span>
            <span className="text-sm font-extrabold tracking-[-0.01em]">{t("app.name")}</span>
          </div>

          <div className="flex max-w-md flex-col gap-6">
            <h2 className="text-3xl leading-tight font-extrabold tracking-[-0.03em] text-balance sm:text-4xl">
              {t("auth.valueProp")}
            </h2>
            <div className="flex flex-col gap-2.5">
              {PILLAR_ORDER.map((pillar) => {
                const target = DEFAULT_TARGETS[pillar];
                const width = `${Math.min(100, (target / AXIS_MAX) * 100)}%`;
                return (
                  <div key={pillar} className="flex items-center gap-3">
                    <span className="w-[150px] shrink-0 truncate text-xs font-semibold text-[#C9CDD1]">
                      {t(`pillar.${pillar}`)}
                    </span>
                    <span className="relative h-2 flex-1 overflow-hidden rounded-pill bg-[#22252A]">
                      <span
                        className="absolute inset-y-0 left-0 rounded-pill"
                        style={{ width, backgroundColor: PILLAR_COLORS[pillar] }}
                      />
                    </span>
                    <span className="w-9 shrink-0 text-right text-[11px] font-bold tabular-nums text-[#8D9298]">
                      {target}%
                    </span>
                  </div>
                );
              })}
            </div>
          </div>

          <p className="text-[11px] text-[#8D9298]">
            {PILLAR_ORDER.map((pillar) => t(`pillar.${pillar}`)).join(" · ")}
          </p>
        </div>

        <div className="flex flex-1 items-center justify-center p-6 sm:p-8 lg:max-w-[420px]">
          {children}
        </div>
      </div>
    </div>
  );
}
