import { Link } from "react-router";
import { useTranslation } from "react-i18next";

import { useFormatCents } from "@/hooks/useFormatCents";
import { formatDate } from "@/lib/format";
import { useListPositions } from "@/lib/api/generated/investments/investments";

interface VaultPositionsSectionProps {
  workspaceId: string;
  accountId: string;
}

/**
 * The fixed-income titles held in an investment account, shown under its caixinhas: what each is worth
 * today and when it pays. Renders nothing when the account has none, so accounts without positions look
 * exactly as before.
 */
export function VaultPositionsSection({ workspaceId, accountId }: VaultPositionsSectionProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const { data: positions } = useListPositions(workspaceId, { status: "ACTIVE" });
  const here = (positions ?? []).filter((position) => position.accountId === accountId);
  if (here.length === 0) return null;

  return (
    <section className="flex flex-col gap-2" aria-label={t("investments.section.title")}>
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-sm font-extrabold text-ink-primary">{t("investments.section.title")}</h2>
        <Link to="/investments" className="text-[11px] font-bold text-link hover:text-link-hover">
          {t("investments.section.viewAll")}
        </Link>
      </div>
      <div className="flex flex-col rounded-card bg-card px-4">
        {here.map((position, index) => (
          <div key={position.id}>
            {index > 0 && <div className="h-px bg-divider-row" />}
            <div className="flex items-center gap-3 py-3">
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="truncate text-[13px] font-bold text-ink-primary">
                  {position.name || t(`simulator.instrument.${position.type}`)}
                </span>
                <span className="truncate text-[11px] text-ink-tertiary">
                  {t("investments.section.matures", { date: formatDate(position.maturityDate) })}
                  {position.vaultName ? ` · ${position.vaultName}` : ""}
                </span>
              </div>
              <span className="shrink-0 text-[13px] font-extrabold tabular-nums text-ink-primary">
                {formatCents(position.estimate?.today.netCents ?? position.principalCents)}
              </span>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
