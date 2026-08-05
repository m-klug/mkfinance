package br.com.mkfinance.application.tag;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Tag;
import br.com.mkfinance.domain.model.TransactionTag;
import br.com.mkfinance.domain.model.TransactionTagId;
import br.com.mkfinance.infrastructure.persistence.TagRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionTagRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contract tag {@code tags} (CT-1, BE-8) — CRUD of {@link Tag}, plus the two things
 * {@code application/transaction/TransactionService} needs to attach tags to a transaction and let
 * the extrato filter by them: {@link #resolveOrCreate} and {@link #replaceTransactionTags}.
 *
 * <p>Tags scope only {@code transaction} (deliberate — {@code card_purchase}/{@code statement}/
 * {@code installment} have no {@code workspace_id} of their own, see {@code V17__transaction_tag.sql}).
 * No JPA relationship backs {@code transaction_tag}; every read here joins the two plain-UUID tables
 * explicitly.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private final TagRepository tagRepository;
    private final TransactionTagRepository transactionTagRepository;

    @Transactional(readOnly = true)
    public List<Tag> list(UUID workspaceId) {
        return tagRepository.findByWorkspaceIdOrderByNameAsc(workspaceId);
    }

    /**
     * Explicit creation (contract {@code createTag}) — 409 TAG_NAME_DUPLICATE, case-insensitive.
     * {@code name} is already guaranteed non-blank by {@code TagRequest}'s {@code @NotBlank}.
     */
    public Tag create(UUID workspaceId, String name) {
        String trimmed = name.trim();
        if (tagRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, trimmed)) {
            throw AppException.tagNameDuplicate(trimmed);
        }
        return saveNew(workspaceId, trimmed);
    }

    /** Renames the tag on every transaction carrying it — the tag row is the single source of truth. */
    public Tag rename(UUID workspaceId, UUID tagId, String name) {
        Tag tag = getOwned(workspaceId, tagId);
        String trimmed = name.trim();
        if (!trimmed.equalsIgnoreCase(tag.getName())
                && tagRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, trimmed)) {
            throw AppException.tagNameDuplicate(trimmed);
        }
        tag.setName(trimmed);
        return tagRepository.save(tag);
    }

    /**
     * Deletes the tag; the {@code transaction_tag} FK ({@code ON DELETE CASCADE}) unlinks it from
     * every transaction by itself. The transactions themselves are never touched (contract).
     */
    public void delete(UUID workspaceId, UUID tagId) {
        Tag tag = getOwned(workspaceId, tagId);
        tagRepository.delete(tag);
    }

    /**
     * Resolves {@code TransactionRequest.tags} (names, not ids — CT-1): looks up what already exists
     * case-insensitively and creates whatever is missing, so typing a brand-new tag while filling a
     * transaction never needs a separate round-trip. Duplicates within {@code rawNames} collapse
     * case-insensitively (first-seen casing wins), and blank entries are dropped rather than rejected.
     *
     * @param rawNames the raw list from the request; {@code null} or empty yields no tags
     */
    public List<Tag> resolveOrCreate(UUID workspaceId, List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) {
            return List.of();
        }
        // LinkedHashMap keyed by lower-case name: dedups case-insensitively while keeping the order —
        // and the first-seen casing — the caller typed them in.
        Map<String, String> distinctByLowerName = new LinkedHashMap<>();
        for (String raw : rawNames) {
            if (raw == null) {
                continue;
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            distinctByLowerName.putIfAbsent(trimmed.toLowerCase(), trimmed);
        }
        if (distinctByLowerName.isEmpty()) {
            return List.of();
        }

        Map<String, Tag> existingByLowerName = new LinkedHashMap<>();
        for (Tag tag : tagRepository.findByWorkspaceIdAndNameLowerIn(workspaceId, distinctByLowerName.keySet())) {
            existingByLowerName.put(tag.getName().toLowerCase(), tag);
        }

        List<Tag> resolved = new ArrayList<>(distinctByLowerName.size());
        for (Map.Entry<String, String> entry : distinctByLowerName.entrySet()) {
            Tag existing = existingByLowerName.get(entry.getKey());
            resolved.add(existing != null ? existing : createRacingSafe(workspaceId, entry.getValue()));
        }
        return resolved;
    }

    /** Replaces the whole tag set of a transaction — the contract's "sending the field replaces". */
    public void replaceTransactionTags(UUID transactionId, List<Tag> tags) {
        transactionTagRepository.deleteByIdTransactionId(transactionId);
        if (tags.isEmpty()) {
            return;
        }
        List<TransactionTag> links = tags.stream()
                .map(tag -> TransactionTag.builder().id(new TransactionTagId(transactionId, tag.getId())).build())
                .toList();
        transactionTagRepository.saveAll(links);
    }

    @Transactional(readOnly = true)
    public List<Tag> tagsOf(UUID transactionId) {
        return tagsOf(List.of(transactionId)).getOrDefault(transactionId, List.of());
    }

    /** Batch tag lookup for a page of transactions — one query instead of one per row. */
    @Transactional(readOnly = true)
    public Map<UUID, List<Tag>> tagsOf(Collection<UUID> transactionIds) {
        if (transactionIds.isEmpty()) {
            return Map.of();
        }
        List<TransactionTag> links = transactionTagRepository.findByIdTransactionIdIn(transactionIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<UUID> tagIds = links.stream().map(link -> link.getId().getTagId()).collect(Collectors.toSet());
        Map<UUID, Tag> tagsById = tagRepository.findAllById(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, tag -> tag));

        Map<UUID, List<Tag>> byTransaction = new LinkedHashMap<>();
        for (TransactionTag link : links) {
            Tag tag = tagsById.get(link.getId().getTagId());
            if (tag == null) {
                continue;
            }
            byTransaction.computeIfAbsent(link.getId().getTransactionId(), id -> new ArrayList<>()).add(tag);
        }
        byTransaction.values().forEach(list -> list.sort(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER)));
        return byTransaction;
    }

    /**
     * The transactions matching any of {@code names} (the extrato's OR filter, CT-1) — {@code null}
     * means "no tag filter given" (list unconstrained), an empty set means "no tag matched" (list
     * must return nothing), never confused with each other.
     *
     * @param names raw filter names; {@code null} or empty means no filter
     */
    @Transactional(readOnly = true)
    public Set<UUID> matchingTransactionIds(UUID workspaceId, List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        List<String> lowerNames = names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase())
                .distinct()
                .toList();
        if (lowerNames.isEmpty()) {
            return Set.of();
        }
        List<Tag> matchingTags = tagRepository.findByWorkspaceIdAndNameLowerIn(workspaceId, lowerNames);
        if (matchingTags.isEmpty()) {
            return Set.of();
        }
        Set<UUID> tagIds = matchingTags.stream().map(Tag::getId).collect(Collectors.toSet());
        return transactionTagRepository.findTransactionIdsByTagIdIn(tagIds);
    }

    private Tag getOwned(UUID workspaceId, UUID tagId) {
        return tagRepository.findByIdAndWorkspaceId(tagId, workspaceId).orElseThrow(AppException::tagNotFound);
    }

    private Tag saveNew(UUID workspaceId, String name) {
        return tagRepository.save(Tag.builder().workspaceId(workspaceId).name(name).build());
    }

    /**
     * Two concurrent requests can both fail to find a tag and race to create it — the unique index
     * ({@code ux_tag_workspace_name_ci}) lets exactly one insert win; the loser re-reads instead of
     * surfacing a raw DATA_INTEGRITY_VIOLATION, mirroring
     * {@code RecurrenceOccurrenceMaterializer}'s materialization race.
     */
    private Tag createRacingSafe(UUID workspaceId, String name) {
        try {
            Tag tag = Tag.builder().workspaceId(workspaceId).name(name).build();
            return tagRepository.saveAndFlush(tag);
        } catch (DataIntegrityViolationException raced) {
            return tagRepository
                    .findByWorkspaceIdAndNameIgnoreCase(workspaceId, name)
                    .orElseThrow(() -> raced);
        }
    }
}
