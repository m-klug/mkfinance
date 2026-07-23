package br.com.mkfinance.application.dashboard;

import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;

/** An account paired with its current/projected balance (R5), as the dashboard lists it. */
public record AccountBalanceView(Account account, Balance balance) {
}
