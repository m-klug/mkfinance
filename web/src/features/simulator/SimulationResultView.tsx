import { Suspense } from "react";
import { useTranslation } from "react-i18next";

import { useFormatCents } from "@/hooks/useFormatCents";
import { formatDate } from "@/lib/format";
import { useContainerWidth } from "@/features/dashboard/useContainerWidth";
import { LazyEvolutionChart } from "@/features/simulator/charts/lazy";
import { formatPercent } from "@/features/simulator/simulatorFormat";
import type { FixedIncomeResult } from "@/lib/api/generated/mKFinanceAPI.schemas";

interface SimulationResultViewProps {
  result: FixedIncomeResult;
  /** Names the simulation when two are shown side by side. */
  title?: string;
}

function Row({ label, value, strong, negative }: { label: string; value: string; strong?: boolean; negative?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-3 py-2.5">
      <span className={strong ? "text-[13px] font-extrabold text-ink-primary" : "text-[13px] text-ink-secondary"}>{label}</span>
      <span
        className={`text-[13px] tabular-nums ${strong ? "font-extrabold text-ink-primary" : "font-bold text-ink-primary"} ${negative ? "text-alert-ink" : ""}`}
      >
        {value}
      </span>
    </div>
  );
}

export function SimulationResultView({ result, title }: SimulationResultViewProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const { ref, width } = useContainerWidth<HTMLDivElement>();

  const exempt = result.incomeTaxCents === 0 && result.iofCents === 0 && (result.type === "LCI" || result.type === "LCA");
  const premiseLine = [
    result.assumptions.cdiAnnualPercent != null && t("simulator.result.assumedCdi", { value: formatPercent(result.assumptions.cdiAnnualPercent) }),
    result.assumptions.selicAnnualPercent != null && t("simulator.result.assumedSelic", { value: formatPercent(result.assumptions.selicAnnualPercent) }),
    result.assumptions.ipcaAnnualPercent != null && t("simulator.result.assumedIpca", { value: formatPercent(result.assumptions.ipcaAnnualPercent) }),
  ].filter(Boolean);

  return (
    <div className="flex min-w-0 flex-col gap-4">
      {title && <h2 className="px-1 text-sm font-extrabold text-ink-primary">{title}</h2>}
      <div className="rounded-card bg-hero p-5 text-hero-foreground">
        <p className="text-xs text-[#9AA0A6]">{t("simulator.result.net")}</p>
        <p className="mt-1 text-3xl font-extrabold tabular-nums" data-testid="net-amount">
          {formatCents(result.netCents)}
        </p>
        <p className="mt-2 text-xs text-[#9AA0A6]">
          {t("simulator.result.netYield", {
            amount: formatCents(result.netYieldCents),
            percent: formatPercent(result.netReturnPercent),
          })}
        </p>
        <p className="mt-3 inline-flex rounded-pill bg-mint px-3 py-1 text-xs font-extrabold text-mint-ink">
          {t("simulator.result.equivalent", { value: formatPercent(result.netAnnualEquivalentPercent) })}
        </p>
      </div>

      <div className="rounded-card bg-card px-5 py-2">
        <Row label={t("simulator.result.invested")} value={formatCents(result.investedCents)} />
        <div className="h-px bg-divider-row" />
        <Row label={t("simulator.result.grossYield")} value={formatCents(result.grossYieldCents)} />
        <div className="h-px bg-divider-row" />
        {exempt ? (
          <Row label={t("simulator.result.incomeTax")} value={t("simulator.result.exempt")} />
        ) : (
          <>
            {result.iofCents > 0 && (
              <>
                <Row label={t("simulator.result.iof")} value={`− ${formatCents(result.iofCents)}`} negative />
                <div className="h-px bg-divider-row" />
              </>
            )}
            <Row label={t("simulator.result.incomeTax")} value={`− ${formatCents(result.incomeTaxCents)}`} negative />
          </>
        )}
        {result.custodyCents > 0 && (
          <>
            <div className="h-px bg-divider-row" />
            <Row label={t("simulator.result.custody")} value={`− ${formatCents(result.custodyCents)}`} negative />
          </>
        )}
        <div className="h-px bg-divider-row" />
        <Row label={t("simulator.result.gross")} value={formatCents(result.grossCents)} />
        <div className="h-px bg-divider-row" />
        <Row label={t("simulator.result.net")} value={formatCents(result.netCents)} strong />
      </div>

      <div className="flex flex-col gap-3 rounded-card bg-card p-5">
        <div className="flex flex-col gap-0.5">
          <h3 className="text-sm font-bold text-ink-primary">{t("simulator.chart.title")}</h3>
          <p className="text-xs text-ink-tertiary">
            {t("simulator.chart.subtitle", { start: formatDate(result.startDate), end: formatDate(result.endDate) })}
          </p>
        </div>
        <div ref={ref} className="min-h-[240px]">
          {width > 0 && (
            <Suspense fallback={null}>
              <LazyEvolutionChart
                data={result.series}
                width={width}
                labels={{
                  invested: t("simulator.chart.invested"),
                  gross: t("simulator.chart.gross"),
                  net: t("simulator.chart.net"),
                  month: (month) => t("simulator.chart.month", { month }),
                }}
              />
            </Suspense>
          )}
        </div>
      </div>

      <p className="px-1 text-xs text-ink-tertiary">
        {premiseLine.length > 0 && `${premiseLine.join(" · ")}. `}
        {t("simulator.result.disclaimer")}
      </p>
    </div>
  );
}
