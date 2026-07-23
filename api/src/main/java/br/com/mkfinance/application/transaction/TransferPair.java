package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.Transaction;
import java.util.UUID;

/**
 * The two mirrored legs of a transfer (R4), always handled together.
 *
 * <p>Only {@link #incoming()} ever carries the contribution pillar, so callers that need to show
 * the pillar alongside the outgoing leg (the transfer endpoint returns the outgoing one) must read
 * it from here rather than from {@link #outgoing()}.
 */
public record TransferPair(Transaction outgoing, Transaction incoming) {

    /**
     * The leg with this id — used to answer an edit with the very row the caller addressed, which
     * may be either leg. Throws when the id belongs to neither, since that means the pair and the
     * id were not loaded together.
     */
    public Transaction legById(UUID id) {
        if (outgoing.getId().equals(id)) {
            return outgoing;
        }
        if (incoming.getId().equals(id)) {
            return incoming;
        }
        throw new IllegalArgumentException("Transaction " + id + " is not a leg of this transfer pair");
    }
}
