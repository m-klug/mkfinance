import { useTranslation } from "react-i18next";

import { useFormatCents } from "@/hooks/useFormatCents";
import { formatPercent } from "@/features/simulator/simulatorFormat";
import type { FixedIncomeComparison, FixedIncomeResult } from "@/lib/api/generated/mKFinanceAPI.schemas";

interface ComparisonVerdictProps {
  comparison: FixedIncomeComparison;
  results: FixedIncomeResult[];
}

const LETTERS = ["A", "B"];

/** Which of the two yields more, net, and by how much; a tie says so. */
export function ComparisonVerdict({ comparison, results }: ComparisonVerdictProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const winner = comparison.betterIndex;
  const tied = winner === undefined;

  return (
    <div className="flex flex-col gap-1.5 rounded-card bg-hero p-5 text-hero-foreground" data-testid="comparison-verdict">
      <p className="text-xl font-extrabold">
        {tied ? t("simulator.compare.tie") : t("simulator.compare.winner", { letter: LETTERS[winner] })}
      </p>
      <p className="text-sm text-[#9AA0A6]">
        {tied
          ? t("simulator.compare.tieDetail")
          : t("simulator.compare.winnerDetail", {
              better: formatPercent(results[winner].netAnnualEquivalentPercent),
              other: formatPercent(results[1 - winner].netAnnualEquivalentPercent),
              amount: formatCents(comparison.netDifferenceCents),
            })}
      </p>
      <p className="mt-1 text-xs text-[#9AA0A6]">{t("simulator.compare.basis")}</p>
    </div>
  );
}
