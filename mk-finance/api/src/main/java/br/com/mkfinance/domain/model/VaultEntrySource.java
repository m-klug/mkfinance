package br.com.mkfinance.domain.model;

/**
 * What put a line in {@link VaultEntry}'s ledger (V19__vault_entry.sql). CONTRIBUTION and
 * WITHDRAWAL mirror a transfer leg (1:1 with a {@link Transaction}), YIELD is one vault's share of
 * a distributed yield (1 transaction : N vaults), and REALLOCATION is money moved between vaults —
 * or to/from the unallocated slice — without any transaction at all (0 transactions : 2 rows).
 */
public enum VaultEntrySource {
    CONTRIBUTION,
    WITHDRAWAL,
    YIELD,
    REALLOCATION
}
