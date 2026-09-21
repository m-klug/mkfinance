import { Cell, Pie, PieChart, Tooltip } from "recharts";

import { useFormatCents } from "@/hooks/useFormatCents";
import { PILLAR_COLORS } from "@/features/dashboard/dashboardFormat";
import type { CategorySpend } from "@/features/dashboard/dashboardData";

interface CategoryPieChartProps {
  spends: CategorySpend[];
  width: number;
}

// Lazy-loaded so Recharts lands in its own async chunk (CONTEXTO §6.8). Given an
// explicit width from the measured container instead of ResponsiveContainer, so
// it never needs ResizeObserver at render time.
export default function CategoryPieChart({ spends, width }: CategoryPieChartProps) {
  const formatCents = useFormatCents();
  const height = 220;
  const radius = Math.min(width, height) / 2 - 8;
  const data = spends.map((spend) => ({
    name: spend.name,
    value: spend.amountCents,
    pillar: spend.pillar,
  }));

  return (
    <PieChart width={width} height={height}>
      <Pie
        data={data}
        dataKey="value"
        nameKey="name"
        cx="50%"
        cy="50%"
        outerRadius={radius}
        innerRadius={radius * 0.55}
        paddingAngle={2}
      >
        {data.map((entry) => (
          <Cell key={entry.name} fill={PILLAR_COLORS[entry.pillar]} />
        ))}
      </Pie>
      <Tooltip
        formatter={(value: number) => formatCents(value)}
        contentStyle={{
          borderRadius: 12,
          border: "1px solid var(--divider-card)",
          background: "var(--card)",
          fontSize: 12,
          color: "var(--ink-primary)",
        }}
        labelStyle={{ color: "var(--ink-primary)" }}
        itemStyle={{ color: "var(--ink-primary)" }}
      />
    </PieChart>
  );
}
