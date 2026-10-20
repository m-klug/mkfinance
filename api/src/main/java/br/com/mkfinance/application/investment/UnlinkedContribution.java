package br.com.mkfinance.application.investment;

import br.com.mkfinance.domain.model.Transaction;
import java.util.UUID;

/** A purchase (the IN leg of a transfer into an investment account) no position claims yet. */
public record UnlinkedContribution(Transaction leg, UUID vaultId) {
}
