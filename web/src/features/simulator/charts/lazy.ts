import { lazy } from "react";

// Same reason as the dashboard charts: Recharts is a separate async chunk.
export const LazyEvolutionChart = lazy(() => import("./EvolutionChart"));
