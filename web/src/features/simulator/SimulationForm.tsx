import { useTranslation } from "react-i18next";

import { MoneyField, SelectField, TextField } from "@/components/form/fields";
import {
  INSTRUMENT_ORDER,
  indexersFor,
  marketRateFor,
  premiseOf,
  withType,
  type FormErrors,
  type SimulationFormState,
} from "@/features/simulator/simulatorForm";
import { formatDate } from "@/lib/format";
import { formatDecimal } from "@/features/simulator/simulatorFormat";
import type { MarketRate } from "@/lib/api/generated/mKFinanceAPI.schemas";

interface SimulationFormProps {
  state: SimulationFormState;
  onChange: (next: SimulationFormState) => void;
  errors: FormErrors;
  /** The current market rates, or undefined while loading / when unavailable. */
  marketRates: MarketRate[] | undefined;
  /** Ids must be unique when two forms share the page (comparator). */
  idPrefix?: string;
}

export function SimulationForm({ state, onChange, errors, marketRates, idPrefix = "sim" }: SimulationFormProps) {
  const { t } = useTranslation();
  const premise = premiseOf(state.indexer);
  const market = premise ? marketRateFor(marketRates, premise) : undefined;

  function patch(partial: Partial<SimulationFormState>) {
    onChange({ ...state, ...partial });
  }

  const premiseHint = market
    ? t("simulator.premise.marketHint", { value: formatDecimal(market.ratePercent), date: formatDate(market.referenceDate) })
    : t("simulator.premise.noMarket");

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <SelectField
        id={`${idPrefix}-type`}
        label={t("simulator.form.type")}
        value={state.type}
        onChange={(event) => onChange(withType(state, event.target.value as SimulationFormState["type"]))}
        options={INSTRUMENT_ORDER.map((type) => ({ value: type, label: t(`simulator.instrument.${type}`) }))}
      />
      <SelectField
        id={`${idPrefix}-indexer`}
        label={t("simulator.form.indexer")}
        value={state.indexer}
        onChange={(event) => patch({ indexer: event.target.value as SimulationFormState["indexer"] })}
        options={indexersFor(state.type).map((indexer) => ({ value: indexer, label: t(`simulator.indexer.${indexer}`) }))}
      />

      <TextField
        id={`${idPrefix}-rate`}
        label={t(`simulator.rateLabel.${state.indexer}`)}
        inputMode="decimal"
        autoComplete="off"
        value={state.rate}
        onChange={(event) => patch({ rate: event.target.value })}
        error={errors.rate ? t(`simulator.errors.${errors.rate}`) : undefined}
      />
      <TextField
        id={`${idPrefix}-months`}
        label={t("simulator.form.months")}
        inputMode="numeric"
        autoComplete="off"
        value={state.months}
        onChange={(event) => patch({ months: event.target.value })}
        error={errors.months ? t(`simulator.errors.${errors.months}`) : undefined}
      />

      <MoneyField
        id={`${idPrefix}-initial`}
        label={t("simulator.form.initial")}
        value={state.initialCents}
        onValueChange={(initialCents) => patch({ initialCents })}
        error={errors.amount ? t(`simulator.errors.${errors.amount}`) : undefined}
      />
      <MoneyField
        id={`${idPrefix}-monthly`}
        label={t("simulator.form.monthly")}
        hint={t("simulator.form.monthlyHint")}
        value={state.monthlyCents}
        onValueChange={(monthlyCents) => patch({ monthlyCents })}
      />

      {premise && (
        <TextField
          id={`${idPrefix}-premise`}
          fieldClassName="sm:col-span-2"
          label={t(`simulator.premise.${premise}`)}
          inputMode="decimal"
          autoComplete="off"
          placeholder={market ? formatDecimal(market.ratePercent) : undefined}
          value={state[premise]}
          onChange={(event) => patch({ [premise]: event.target.value })}
          hint={premiseHint}
          error={errors.premise ? t(`simulator.errors.${errors.premise}`) : undefined}
        />
      )}
    </div>
  );
}
