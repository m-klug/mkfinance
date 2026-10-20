package br.com.mkfinance.application.fixedincome;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeResult;

/** One simulation with the parameters it actually ran with (assumptions resolved, start date set). */
public record FixedIncomeSimulation(FixedIncomeParams params, FixedIncomeResult result) {
}
