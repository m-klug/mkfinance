import { Bar, BarChart, CartesianGrid, Cell, Tooltip, XAxis, YAxis } from "recharts";

import { useFormatCents } from "@/hooks/useFormatCents";

export interface IncomeExpenseDatum {
  label: string;
  amountCents: number;
  kind: "income" | "expense";
}

interface IncomeExpenseChartProps {
  data: IncomeExpenseDatum[];
  width: number;
}

// Mint for income, chart-gray-1 for expense — the handoff's income×expense bars
// stay in the neutral/mint palette rather than a red/green traffic-light pair
// (design_handoff_mk_finance/README.md §Cores dos 6 pilares / gráficos).
const COLORS: Record<IncomeExpenseDatum["kind"], string> = {
  income: "var(--mint)",
  expense: "var(--chart-gray-1)",
};

// Lazy-loaded (Recharts stays in its own chunk, CONTEXTO §6.8). Explicit width
// from the measured container keeps ResponsiveContainer/ResizeObserver out.
export default function IncomeExpenseChart({ data, width }: IncomeExpenseChartProps) {
  const formatCents = useFormatCents();
  const height = 220;
  return (
    <BarChart width={width} height={height} data={data} margin={{ top: 8, right: 8, bottom: 8, left: 8 }}>
      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--divider-row)" />
      <XAxis dataKey="label" tick={{ fontSize: 12, fill: "var(--ink-tertiary)" }} axisLine={{ stroke: "var(--divider-card)" }} tickLine={false} />
      <YAxis
        tick={{ fontSize: 12, fill: "var(--ink-tertiary)" }}
        width={72}
        axisLine={false}
        tickLine={false}
        tickFormatter={(value: number) => formatCents(value)}
      />
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
      <Bar dataKey="amountCents" radius={[8, 8, 4, 4]}>
        {data.map((datum) => (
          <Cell key={datum.label} fill={COLORS[datum.kind]} />
        ))}
      </Bar>
    </BarChart>
  );
}
