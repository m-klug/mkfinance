import { CartesianGrid, Legend, Line, LineChart, Tooltip, XAxis, YAxis } from "recharts";

import { useFormatCents } from "@/hooks/useFormatCents";

export interface EvolutionDatum {
  month: number;
  investedCents: number;
  grossCents: number;
  netCents: number;
}

interface EvolutionChartProps {
  data: EvolutionDatum[];
  width: number;
  labels: { invested: string; gross: string; net: string; month: (month: number) => string };
}

// Lazy-loaded like the dashboard charts: Recharts stays out of the initial bundle, and the width
// comes from the measured container so no ResponsiveContainer/ResizeObserver is needed here.
export default function EvolutionChart({ data, width, labels }: EvolutionChartProps) {
  const formatCents = useFormatCents();
  return (
    <LineChart width={width} height={240} data={data} margin={{ top: 8, right: 8, bottom: 8, left: 8 }}>
      <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="var(--divider-row)" />
      <XAxis
        dataKey="month"
        tick={{ fontSize: 12, fill: "var(--ink-tertiary)" }}
        axisLine={{ stroke: "var(--divider-card)" }}
        tickLine={false}
        tickFormatter={(month: number) => labels.month(month)}
        minTickGap={24}
      />
      <YAxis
        tick={{ fontSize: 12, fill: "var(--ink-tertiary)" }}
        width={84}
        // A zero baseline would flatten a yield of a few percent into a straight line.
        domain={[(min: number) => Math.floor(min * 0.98), (max: number) => Math.ceil(max * 1.02)]}
        axisLine={false}
        tickLine={false}
        tickFormatter={(value: number) => formatCents(value)}
      />
      <Tooltip
        formatter={(value: number, name: string) => [formatCents(value), name]}
        labelFormatter={(month: number) => labels.month(month)}
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
      <Legend wrapperStyle={{ fontSize: 12, color: "var(--ink-secondary)" }} />
      <Line type="monotone" dataKey="investedCents" name={labels.invested} stroke="var(--chart-gray-2)" strokeWidth={2} strokeDasharray="4 4" dot={false} />
      <Line type="monotone" dataKey="grossCents" name={labels.gross} stroke="var(--chart-gray-1)" strokeWidth={2} dot={false} />
      <Line type="monotone" dataKey="netCents" name={labels.net} stroke="var(--mint)" strokeWidth={3} dot={false} />
    </LineChart>
  );
}
