import { lazy } from "react";

// React.lazy + dynamic import make Recharts a separate async chunk so it never
// weighs on the initial bundle (CONTEXTO §6.8). Both charts import Recharts, so
// Rollup dedupes it into one shared vendor chunk they pull on demand.
export const LazyCategoryPieChart = lazy(() => import("./CategoryPieChart"));
export const LazyIncomeExpenseChart = lazy(() => import("./IncomeExpenseChart"));
