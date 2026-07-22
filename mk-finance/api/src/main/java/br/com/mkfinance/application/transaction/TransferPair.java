package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.Transaction;

/**
 * The two mirrored legs of a transfer (R4), always handled together.
 *
 * <p>Only {@link #incoming()} ever carries the contribution pillar, so callers that need to show
 * the pillar alongside the outgoing leg (the transfer endpoint returns the outgoing one) must read
 * it from here rather than from {@link #outgoing()}.
 */
public record TransferPair(Transaction outgoing, Transaction incoming) {
}
