import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { translateApiError } from "@/lib/errors";
import { useListMarketRates } from "@/lib/api/generated/market-rates/market-rates";
import { useSimulateFixedIncome } from "@/lib/api/generated/fixed-income/fixed-income";
import {
  FixedIncomeSimulationItemType,
  type FixedIncomeSimulationResponse,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import { ComparisonVerdict } from "@/features/simulator/ComparisonVerdict";
import { SimulationForm } from "@/features/simulator/SimulationForm";
import { SimulationResultView } from "@/features/simulator/SimulationResultView";
import {
  DEFAULT_FORM,
  toRequestItem,
  validateForm,
  withType,
  type FormErrors,
  type SimulationFormState,
} from "@/features/simulator/simulatorForm";

type Mode = "single" | "compare";

const LETTERS = ["A", "B"];

/** A starting point for the second simulation: the usual rival of a CDB, with the same amounts and term. */
function seedSecond(first: SimulationFormState): SimulationFormState {
  return {
    ...withType(DEFAULT_FORM, FixedIncomeSimulationItemType.LCI),
    initialCents: first.initialCents,
    monthlyCents: first.monthlyCents,
    months: first.months,
  };
}

export default function SimulatorPage() {
  const { t } = useTranslation();
  const [mode, setMode] = useState<Mode>("single");
  const [forms, setForms] = useState<SimulationFormState[]>([DEFAULT_FORM, DEFAULT_FORM]);
  const [errors, setErrors] = useState<FormErrors[]>([{}, {}]);
  const [response, setResponse] = useState<FixedIncomeSimulationResponse | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const { data: marketRates } = useListMarketRates();
  const simulate = useSimulateFixedIncome();

  const active = mode === "compare" ? 2 : 1;

  function changeMode(next: Mode) {
    setMode(next);
    setResponse(null);
    setSubmitError(null);
    setErrors([{}, {}]);
    // First time into the comparator: offer a rival instead of an identical copy of the empty form.
    if (next === "compare" && forms[1] === DEFAULT_FORM) {
      setForms([forms[0], seedSecond(forms[0])]);
    }
  }

  function handleChange(index: number, next: SimulationFormState) {
    setForms((current) => current.map((form, i) => (i === index ? next : form)));
    setErrors([{}, {}]);
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const found = forms.map((form, i) => (i < active ? validateForm(form) : {}));
    setErrors(found);
    setSubmitError(null);
    if (found.some((errorsOfForm) => Object.keys(errorsOfForm).length > 0)) return;

    simulate.mutate(
      { data: { simulations: forms.slice(0, active).map(toRequestItem) } },
      {
        onSuccess: setResponse,
        onError: (error) => {
          setResponse(null);
          setSubmitError(translateApiError(t, error));
        },
      },
    );
  }

  const results = response?.results ?? [];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-1 pt-1">
        <h1 className="text-xl font-extrabold tracking-[-0.025em] text-ink-primary">{t("simulator.title")}</h1>
        <p className="text-sm text-ink-tertiary">{t("simulator.subtitle")}</p>
      </div>

      <SegmentedControl
        options={[
          { value: "single", label: t("simulator.mode.single") },
          { value: "compare", label: t("simulator.mode.compare") },
        ]}
        value={mode}
        onChange={changeMode}
      />

      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        <div className={mode === "compare" ? "grid gap-4 lg:grid-cols-2" : "grid gap-4"}>
          {forms.slice(0, active).map((form, index) => (
            <div
              key={index}
              role="group"
              aria-label={mode === "compare" ? t("simulator.compare.label", { letter: LETTERS[index] }) : undefined}
              className="flex min-w-0 flex-col gap-4 rounded-card bg-card p-5"
            >
              {mode === "compare" && (
                <h2 className="text-sm font-extrabold text-ink-primary">
                  {t("simulator.compare.label", { letter: LETTERS[index] })}
                </h2>
              )}
              <SimulationForm
                state={form}
                onChange={(next) => handleChange(index, next)}
                errors={errors[index]}
                marketRates={marketRates}
                idPrefix={`sim-${LETTERS[index].toLowerCase()}`}
              />
            </div>
          ))}
        </div>

        {submitError && (
          <p role="alert" className="text-sm text-destructive">
            {submitError}
          </p>
        )}
        <Button type="submit" size="lg" disabled={simulate.isPending}>
          {simulate.isPending
            ? t("simulator.form.simulating")
            : mode === "compare"
              ? t("simulator.compare.submit")
              : t("simulator.form.submit")}
        </Button>
      </form>

      {response && results.length === 1 && <SimulationResultView result={results[0]} />}
      {response && results.length === 2 && (
        <>
          {response.comparison && <ComparisonVerdict comparison={response.comparison} results={results} />}
          <div className="grid gap-4 lg:grid-cols-2">
            {results.map((result, index) => (
              <SimulationResultView
                key={index}
                result={result}
                title={t("simulator.compare.label", { letter: LETTERS[index] })}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
