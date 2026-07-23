import { Bar, BarChart, CartesianGrid, Cell, Tooltip, XAxis, YAxis } from "recharts";

import { formatCents } from "@/lib/format";

export interface IncomeExpenseDatum {
  label: string;
  amountCents: number;
  kind: "income" | "expense";
}

interface IncomeExpenseChartProps {
  data: IncomeExpenseDatum[];
  width: number;
}

const COLORS: Record<IncomeExpenseDatum["kind"], string> = {
  income: "#16a34a",
  expense: "#dc2626",
};

// Lazy-loaded (Recharts stays in its own chunk, CONTEXTO §6.8). Explicit width
// from the measured container keeps ResponsiveContainer/ResizeObserver out.
export default function IncomeExpenseChart({ data, width }: IncomeExpenseChartProps) {
  const height = 220;
  return (
    <BarChart width={width} height={height} data={data} margin={{ top: 8, right: 8, bottom: 8, left: 8 }}>
      <CartesianGrid strokeDasharray="3 3" vertical={false} />
      <XAxis dataKey="label" tick={{ fontSize: 12 }} />
      <YAxis
        tick={{ fontSize: 12 }}
        width={72}
        tickFormatter={(value: number) => formatCents(value)}
      />
      <Tooltip formatter={(value: number) => formatCents(value)} />
      <Bar dataKey="amountCents" radius={[4, 4, 0, 0]}>
        {data.map((datum) => (
          <Cell key={datum.label} fill={COLORS[datum.kind]} />
        ))}
      </Bar>
    </BarChart>
  );
}
