package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.TransactionTag;
import br.com.mkfinance.domain.model.TransactionTagId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionTagRepository extends JpaRepository<TransactionTag, TransactionTagId> {

    /** Every {@code (transaction, tag)} row of the given transactions, for a batch tag lookup. */
    List<TransactionTag> findByIdTransactionIdIn(Collection<UUID> transactionIds);

    void deleteByIdTransactionId(UUID transactionId);

    /**
     * The transactions carrying any of these tags — the "OR" side of the extrato's {@code tags}
     * filter (CT-1). {@code tagIds} is already scoped to the workspace by the caller
     * ({@code TagRepository.findByWorkspaceIdAndNameLowerIn}), so this query needs no workspace
     * predicate of its own.
     */
    @Query("select distinct tt.id.transactionId from TransactionTag tt where tt.id.tagId in :tagIds")
    Set<UUID> findTransactionIdsByTagIdIn(@Param("tagIds") Collection<UUID> tagIds);
}
