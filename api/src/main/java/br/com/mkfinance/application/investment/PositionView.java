package br.com.mkfinance.application.investment;

import br.com.mkfinance.domain.model.FixedIncomePosition;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomePositionValuator.Estimate;

/**
 * A position with what the app estimates for it today. {@code estimate} is null for a settled
 * position (its real result is on the position itself), and when it cannot be computed — a
 * post-fixed title with no stored index and no current rate to project the days to come.
 */
public record PositionView(FixedIncomePosition position, Estimate estimate) {
}
