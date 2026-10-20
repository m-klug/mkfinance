package br.com.mkfinance.application.transaction;

import br.com.mkfinance.application.investment.FixedIncomePositionGuard;
import br.com.mkfinance.application.vault.VaultBalanceService;
import br.com.mkfinance.application.vault.VaultLinkService;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
import br.com.mkfinance.domain.model.Vault;
import br.com.mkfinance.domain.model.VaultEntry;
import br.com.mkfinance.domain.model.VaultEntrySource;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import br.com.mkfinance.infrastructure.persistence.VaultEntryRepository;
import br.com.mkfinance.infrastructure.persistence.VaultRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The mirrored-pair engine (PLANEJAMENTO §5 R4 and R9).
 *
 * <p>A transfer is never a single row: it is an {@link TransferDirection#OUT} leg on the source
 * account and an {@link TransferDirection#IN} leg on the destination, each pointing at the other
 * through {@code transfer_pair_id}. Every mutation here — create, edit, settle, delete — touches
 * <em>both</em> legs inside one transaction, so the two sides can never drift apart. Because the
 * legs carry opposite signs on different accounts, a transfer moves money without ever producing
 * income or expense (R4).
 *
 * <p>Contribution ("aporte", R9): when the destination is an INVESTMENT or SAVINGS account, the
 * transfer consumes a pillar's allocation without being an expense. The pillar defaults to the
 * destination account's {@code defaultContributionPillar} and can be overridden per transfer — the
 * same brokerage account may receive a GOALS contribution one month and a FINANCIAL_FREEDOM one
 * the next. It is stored on the IN leg only (DB check constraint), so summing it per pillar can
 * never count a single transfer twice.
 *
 * <p><strong>Caixinhas (BE-10, DESENVOLVIMENTO-CAIXINHAS.md §4.4).</strong> Once an account has at
 * least one non-archived vault, every transfer leg touching it must name one: the contribution leg
 * because that is where the pillar now lives (the vault's, not the account's default anymore), and
 * the withdrawal leg symmetrically, so a resgate can never drain money the composition never counted
 * as allocated. An account with no vaults — or only archived ones — keeps working exactly as before
 * BE-10; nothing here changes for it. The vault ledger itself is never written by this class —
 * {@link #vaultLinkService} owns that (§4.4: "TransferService não ganha segunda responsabilidade").
 *
 * <p>This service deliberately does not depend on {@code TransactionService}: the dependency runs
 * the other way, so there is no cycle.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final VaultRepository vaultRepository;
    private final VaultEntryRepository vaultEntryRepository;
    private final VaultLinkService vaultLinkService;
    private final VaultBalanceService vaultBalanceService;
    private final FixedIncomePositionGuard positionGuard;

    /**
     * Creates both legs atomically and returns them. Transfers start SETTLED — the contract's
     * {@code TransferRequest} has no status field; a transfer can still be moved to PENDING later
     * through the transaction PATCH endpoint.
     *
     * <p>Caixinhas (§4.4): the source leg is inspected too, not just the destination — "resgate
     * também exige caixinha". The ledger rows are only written once both legs exist ({@link #link}
     * flushes them first): {@code vault_entry}'s FK needs a real {@code transaction_id} (armadilha 5,
     * §7 — a three-step {@code saveAndFlush} dance, mirrored here one level up).
     */
    public TransferPair create(UUID workspaceId, TransferInput input) {
        if (input.fromAccountId() == null
                || input.toAccountId() == null
                || input.fromAccountId().equals(input.toAccountId())) {
            throw AppException.insufficientTransferAccounts();
        }
        Account source = TransactionLookup.ownedAccount(accountRepository, workspaceId, input.fromAccountId());
        Account destination = TransactionLookup.ownedAccount(accountRepository, workspaceId, input.toAccountId());
        Vault sourceVault = resolveVault(workspaceId, source.getId(), input.fromVaultId());
        Vault destinationVault = resolveVault(workspaceId, destination.getId(), input.toVaultId());
        checkVaultRequirement(source.getId(), sourceVault);
        ContributionPillar pillar =
                resolveContributionPillar(destination, destinationVault, input.contributionPillar(), true);

        Transaction outgoing = leg(
                workspaceId,
                source.getId(),
                TransferDirection.OUT,
                input.amountCents(),
                input.date(),
                input.description(),
                TransactionStatus.SETTLED,
                null,
                externalIdFor(input, source.getId()));
        Transaction incoming = leg(
                workspaceId,
                destination.getId(),
                TransferDirection.IN,
                input.amountCents(),
                input.date(),
                input.description(),
                TransactionStatus.SETTLED,
                pillar,
                externalIdFor(input, destination.getId()));

        TransferPair pair = link(outgoing, incoming);

        if (sourceVault != null) {
            vaultLinkService.link(workspaceId, sourceVault.getId(), outgoing.getId(), VaultEntrySource.WITHDRAWAL);
        }
        if (destinationVault != null) {
            vaultLinkService.link(workspaceId, destinationVault.getId(), incoming.getId(), VaultEntrySource.CONTRIBUTION);
        }

        return pair;
    }

    /**
     * Applies an edit to both legs, addressed through either one. Shared facts (amount, date,
     * description, note, status) are mirrored; {@code accountId} only moves the leg being edited,
     * since each side lives on its own account.
     *
     * <p>The contribution pillar (R9) is resolved as an input, not read back as state: an explicit
     * {@link TransferUpdateInput#contributionPillar()} re-targets the contribution and is refused
     * with INVALID_CONTRIBUTION_PILLAR when the destination takes none — exactly as on creation,
     * from the same rule ({@link #resolveContributionPillar}). Omitting it carries the current pillar
     * forward against whatever account the IN leg now points at: moving a contribution to a checking
     * account drops it, while moving it to another investment account keeps the pillar already
     * chosen (falling back to the new account's default only when there is none), so a deliberate
     * choice is never silently overwritten by a destination's default. This is always about the
     * <em>incoming</em> leg, regardless of which leg the caller addressed — a pillar has never had
     * meaning on the outgoing side.
     *
     * <p>Caixinhas (§4.4): {@code vaultId} is symmetric to {@code accountId} — it only ever re-points
     * the <em>addressed</em> leg's own vault (contribution or withdrawal, whichever direction that
     * leg is). Omitted, the leg's current link carries forward while it stays on the same account;
     * once {@code accountId} moves it, the old link is discarded and the requirement is evaluated
     * fresh against the new account, exactly like the pillar rule one level up already does. Once
     * either side's account has an active vault, that side's link is re-validated on every edit that
     * reaches it — the same "re-derive from the ledger's current truth" discipline
     * {@code VaultBalanceService.assertNotOverdrawn} documents for the balance guard.
     *
     * <p>Loads the addressed leg itself (through {@code transactionRepository}, not
     * {@code TransactionService}) so the endpoint needs a single call and this service keeps its
     * one-directional relationship with {@link TransactionService}. Returns the pair so the caller
     * can answer with the leg it addressed while echoing the pillar, which lives on the IN leg.
     *
     * @throws AppException TRANSACTION_NOT_FOUND when the id is not a transfer leg — a plain
     *     INCOME/EXPENSE id included, since "no such transfer" is the honest answer and leaks
     *     nothing about what the id really is
     */
    public TransferPair update(UUID workspaceId, UUID transactionId, TransferUpdateInput input) {
        Transaction leg = transactionRepository
                .findByIdAndWorkspaceId(transactionId, workspaceId)
                .orElseThrow(AppException::transactionNotFound);
        if (!leg.isTransfer()) {
            throw AppException.transactionNotFound();
        }
        Transaction sibling = requireSibling(leg);
        guardPositionPurchase(leg, sibling, input);

        boolean legMoved = false;
        if (input.accountId() != null && !input.accountId().equals(leg.getAccountId())) {
            Account account = TransactionLookup.ownedAccount(accountRepository, workspaceId, input.accountId());
            if (account.getId().equals(sibling.getAccountId())) {
                throw AppException.insufficientTransferAccounts();
            }
            leg.setAccountId(account.getId());
            legMoved = true;
        }

        for (Transaction side : List.of(leg, sibling)) {
            side.setAmountCents(input.amountCents());
            side.setDate(input.date());
            side.setDescription(input.description());
            side.setNote(input.note());
            if (input.status() != null) {
                side.setStatus(input.status());
            }
        }

        Transaction incoming = leg.getTransferDirection() == TransferDirection.IN ? leg : sibling;
        Transaction outgoing = incoming == leg ? sibling : leg;
        boolean addressedIsIncoming = leg == incoming;
        Account destination = TransactionLookup.ownedAccount(accountRepository, workspaceId, incoming.getAccountId());
        Account source = TransactionLookup.ownedAccount(accountRepository, workspaceId, outgoing.getAccountId());

        Vault currentAddressedVault = currentVaultOf(workspaceId, leg.getId());
        UUID targetVaultId = input.vaultId() != null
                ? input.vaultId()
                : (legMoved || currentAddressedVault == null ? null : currentAddressedVault.getId());
        Account addressedAccount = addressedIsIncoming ? destination : source;
        Vault addressedVault = resolveVault(workspaceId, addressedAccount.getId(), targetVaultId);

        Vault destinationVault = addressedIsIncoming ? addressedVault : currentVaultOf(workspaceId, incoming.getId());
        Vault sourceVault = addressedIsIncoming ? currentVaultOf(workspaceId, outgoing.getId()) : addressedVault;

        // An explicit pillar re-targets the contribution; omitting it carries the current one forward
        // while the destination still accepts one (dropped, not rejected, on a plain account) and has
        // no vault of its own to decide instead — either way resolveContributionPillar has the final
        // say, so the create/edit rule stays in one place.
        ContributionPillar legacyPillar = isContributionTarget(destination) ? incoming.getContributionPillar() : null;
        ContributionPillar requested = input.contributionPillar() != null
                ? input.contributionPillar()
                : (destinationVault == null ? legacyPillar : null);
        // The vault requirement only applies to the leg actually being re-pointed — moved to another
        // account, or handed an explicit vaultId. A leg left alone keeps whatever it has, including
        // nothing: a contribution booked before its account had vaults must not become uneditable the
        // day the first caixinha is created (§4.0's "nada migra em produção").
        boolean repointed = legMoved || input.vaultId() != null;
        outgoing.setContributionPillar(null);
        incoming.setContributionPillar(
                resolveContributionPillar(destination, destinationVault, requested, repointed && addressedIsIncoming));
        if (!addressedIsIncoming && repointed) {
            // resolveContributionPillar above only ever inspects the destination; the source's own
            // vault requirement (§4.4's symmetric "resgate também exige caixinha") is checked here
            // when it is the leg actually being edited.
            checkVaultRequirement(source.getId(), sourceVault);
        }

        transactionRepository.saveAll(List.of(outgoing, incoming));

        VaultEntrySource addressedSource = addressedIsIncoming ? VaultEntrySource.CONTRIBUTION : VaultEntrySource.WITHDRAWAL;
        vaultLinkService.relink(
                workspaceId, leg.getId(), addressedVault != null ? addressedVault.getId() : null, addressedSource);

        return new TransferPair(outgoing, incoming);
    }

    /**
     * Settles both legs — a half-settled transfer would make one account's balance wrong.
     *
     * <p>Caixinhas (§4.4): a PENDING contribution never trips a vault's balance guard by itself (it
     * has not moved {@code currentCents} yet), so the guard is asked here, once both legs are
     * SETTLED — the same instant a vault can actually go negative for real.
     */
    public Transaction settle(Transaction leg) {
        Transaction sibling = requireSibling(leg);
        leg.setStatus(TransactionStatus.SETTLED);
        sibling.setStatus(TransactionStatus.SETTLED);
        transactionRepository.saveAll(List.of(leg, sibling));
        assertLinkedVaultsNotOverdrawn(leg.getWorkspaceId(), leg.getId(), sibling.getId());
        return leg;
    }

    /**
     * Deletes both legs. The two rows reference each other, so the mutual foreign key has to be
     * broken and flushed before either row can go.
     *
     * <p>Caixinhas (§7 armadilha 6): {@link VaultLinkService#unlink} is called <strong>before</strong>
     * the transaction rows are deleted, not after — deleting first would leave a stale
     * {@code VaultEntry} in this same transaction's persistence context even though the database's
     * {@code ON DELETE CASCADE} already dropped the row underneath it.
     */
    public void delete(Transaction leg) {
        Transaction sibling = requireSibling(leg);
        positionGuard.assertNotReferenced(leg.getId());
        positionGuard.assertNotReferenced(sibling.getId());
        vaultLinkService.unlink(leg.getId());
        vaultLinkService.unlink(sibling.getId());
        leg.setTransferPairId(null);
        sibling.setTransferPairId(null);
        transactionRepository.saveAllAndFlush(List.of(leg, sibling));
        transactionRepository.deleteAll(List.of(leg, sibling));
        transactionRepository.flush();
    }

    /**
     * A fixed-income position copied this transfer's amount, date and account when it was born, so
     * none of them may move while it exists (TRANSACTION_HAS_POSITION): delete the position first.
     * The description, the note, the pillar and the vault stay editable — the position does not
     * depend on them.
     */
    private void guardPositionPurchase(Transaction leg, Transaction sibling, TransferUpdateInput input) {
        Transaction incoming = leg.getTransferDirection() == TransferDirection.IN ? leg : sibling;
        if (!positionGuard.isContribution(incoming.getId())) {
            return;
        }
        boolean moves = input.amountCents() != incoming.getAmountCents()
                || !input.date().equals(incoming.getDate())
                || (input.accountId() != null && !input.accountId().equals(leg.getAccountId()))
                || (input.status() != null && input.status() != incoming.getStatus());
        if (moves) {
            throw AppException.transactionHasPosition();
        }
    }

    /**
     * Batch lookup of each transaction's linked vault, if any (contract {@code Transaction.vaultId})
     * — mirrors {@code TagService.tagsOf(Collection)}: one query for a whole page of the extrato
     * instead of one per row. Only CONTRIBUTION/WITHDRAWAL legs ever resolve — {@code amount_cents IS
     * NULL} is the mirror marker (V19) — so a YIELD transaction's id never comes back here, since it
     * cannot collapse a 1-transaction-to-N-vaults split into a single {@code vaultId}.
     */
    @Transactional(readOnly = true)
    public Map<UUID, UUID> vaultIdsOf(Collection<UUID> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        return vaultEntryRepository.findByTransactionIdInAndAmountCentsIsNull(transactionIds).stream()
                .collect(Collectors.toMap(VaultEntry::getTransactionId, VaultEntry::getVaultId));
    }

    /**
     * R9 + DESENVOLVIMENTO-CAIXINHAS.md §4.4. Returns the pillar the destination account's
     * contribution consumes, or null when the destination is not a contribution target at all.
     *
     * <p>Once the destination has at least one active vault, {@code destinationVault} — already
     * resolved and scoped to this account by {@link #resolveVault} — takes over deciding the pillar:
     * {@code requested} only still matters as an advisory check that must agree with it
     * (VAULT_PILLAR_CONFLICT otherwise). Accepting a {@code requested} that merely repeats the
     * vault's own pillar is deliberate, not laxity — the client round-trips the value it just read
     * back, and refusing an unchanged answer would break editing (armadilha 8, §7).
     */
    /**
     * @param enforceVaultRequirement false only when editing a leg that is <em>not</em> being
     *     re-pointed (§4.4). A contribution booked before its account had any vault has none, and
     *     creating the first vault must not make that row uneditable forever — "nada migra em
     *     produção" (§4.0) would be a dead letter if it did. Such a contribution keeps the pillar it
     *     already consumed and its money simply stays in the unallocated slice, which is what that
     *     slice is for (§4.3). Re-point the leg and the requirement applies in full again.
     */
    private ContributionPillar resolveContributionPillar(
            Account destination,
            Vault destinationVault,
            ContributionPillar requested,
            boolean enforceVaultRequirement) {
        if (!isContributionTarget(destination)) {
            // A pillar — or a vault — on a plain checking/cash destination would silently inflate
            // that pillar, or claim a caixinha a non-investment account can never have.
            if (requested != null) {
                throw AppException.invalidContributionPillar();
            }
            if (destinationVault != null) {
                throw AppException.vaultNotFound();
            }
            return null;
        }
        if (enforceVaultRequirement) {
            checkVaultRequirement(destination.getId(), destinationVault);
        }
        if (destinationVault == null) {
            ContributionPillar resolved = requested != null
                    ? requested
                    : ContributionPillar.fromPillar(destination.getDefaultContributionPillar());
            if (resolved == null) {
                throw AppException.contributionPillarRequired();
            }
            return resolved;
        }
        if (requested != null && requested != destinationVault.getContributionPillar()) {
            throw AppException.vaultPillarConflict();
        }
        return destinationVault.getContributionPillar();
    }

    /**
     * §4.4's "hasVaults" gate, shared by both legs of a transfer: once an account has any
     * non-archived vault, every leg touching it — contribution or withdrawal alike — must name one.
     * An account with no vaults, or only archived ones, is untouched by this (comportamento de hoje).
     *
     * @param vault already resolved via {@link #resolveVault}, scoped to this same account — so one
     *     of the account's own <em>archived</em> vaults can still reach here when {@code hasVaults} is
     *     false (every one of its vaults is archived): that combination is treated as "not found",
     *     the same doorway back to "sem caixinha" mode that archiving the last vault promises (§4.7)
     */
    private void checkVaultRequirement(UUID accountId, Vault vault) {
        boolean hasVaults = vaultRepository.existsByAccountIdAndArchivedFalse(accountId);
        if (!hasVaults) {
            if (vault != null) {
                throw AppException.vaultNotFound();
            }
            return;
        }
        if (vault == null) {
            throw AppException.vaultRequired();
        }
        if (vault.isArchived()) {
            throw AppException.vaultArchived();
        }
    }

    /**
     * Resolves a vault addressed by id, scoped to both the workspace (R8) and the account it must
     * belong to. {@code null} in means "no vault addressed", which is still a valid outcome — most
     * accounts have none.
     *
     * @throws AppException VAULT_NOT_FOUND for an unknown id, another workspace's, or one that
     *     belongs to a different account than the leg being resolved
     */
    private Vault resolveVault(UUID workspaceId, UUID accountId, UUID vaultId) {
        if (vaultId == null) {
            return null;
        }
        Vault vault = vaultRepository.findByIdAndWorkspaceId(vaultId, workspaceId).orElseThrow(AppException::vaultNotFound);
        if (!vault.getAccountId().equals(accountId)) {
            throw AppException.vaultNotFound();
        }
        return vault;
    }

    /** The vault currently mirrored by a transfer leg's ledger row, if any — used by {@link #update}. */
    private Vault currentVaultOf(UUID workspaceId, UUID transactionId) {
        return vaultEntryRepository
                .findByTransactionIdAndAmountCentsIsNull(transactionId)
                .map(entry -> vaultRepository.findByIdAndWorkspaceId(entry.getVaultId(), workspaceId).orElse(null))
                .orElse(null);
    }

    /** §4.4 — asked once both legs are SETTLED, since a PENDING movement cannot overdraw a vault yet. */
    private void assertLinkedVaultsNotOverdrawn(UUID workspaceId, UUID... transactionIds) {
        for (UUID transactionId : transactionIds) {
            vaultEntryRepository
                    .findByTransactionIdAndAmountCentsIsNull(transactionId)
                    .ifPresent(entry -> vaultBalanceService.assertNotOverdrawn(workspaceId, entry.getVaultId()));
        }
    }

    /** R9 — only investment/reserve accounts receive contributions. */
    private boolean isContributionTarget(Account account) {
        return account.getType() == AccountType.INVESTMENT || account.getType() == AccountType.SAVINGS;
    }

    /**
     * Persists the two legs and makes them point at each other. The ids only exist after the
     * insert, so this is necessarily a three-step dance: insert the outgoing leg, insert the
     * incoming one pointing back at it, then close the loop.
     */
    private TransferPair link(Transaction outgoing, Transaction incoming) {
        transactionRepository.saveAndFlush(outgoing);
        incoming.setTransferPairId(outgoing.getId());
        transactionRepository.saveAndFlush(incoming);
        outgoing.setTransferPairId(incoming.getId());
        transactionRepository.saveAndFlush(outgoing);
        return new TransferPair(outgoing, incoming);
    }

    private Transaction requireSibling(Transaction leg) {
        if (leg.getTransferPairId() == null) {
            // Guaranteed by the DB (ck_transaction_pair) — reaching here means corrupted data.
            throw new IllegalStateException("Transfer leg " + leg.getId() + " has no pair");
        }
        return transactionRepository
                .findByIdAndWorkspaceId(leg.getTransferPairId(), leg.getWorkspaceId())
                .orElseThrow(() -> new IllegalStateException("Missing transfer pair for leg " + leg.getId()));
    }

    private Transaction leg(
            UUID workspaceId,
            UUID accountId,
            TransferDirection direction,
            long amountCents,
            LocalDate date,
            String description,
            TransactionStatus status,
            ContributionPillar contributionPillar,
            String externalId) {
        return Transaction.builder()
                .workspaceId(workspaceId)
                .accountId(accountId)
                .categoryId(null)
                .type(TransactionType.TRANSFER)
                .transferDirection(direction)
                .amountCents(amountCents)
                .date(date)
                .description(description)
                .status(status)
                .contributionPillar(contributionPillar)
                .externalId(externalId)
                .build();
    }

    /** The bank's id goes only on the leg of the account the statement was imported into. */
    private static String externalIdFor(TransferInput input, UUID accountId) {
        return accountId.equals(input.externalIdAccountId()) ? input.externalId() : null;
    }
}
