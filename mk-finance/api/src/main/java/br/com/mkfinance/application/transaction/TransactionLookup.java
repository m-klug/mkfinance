package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.Category;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.CategoryRepository;
import java.util.UUID;

/**
 * Workspace-scoped lookups shared by {@code TransactionService} and {@code TransferService}.
 *
 * <p>Every read goes through {@code findByIdAndWorkspaceId}, so referencing another workspace's
 * account or category is indistinguishable from referencing a nonexistent one — a 404, never a
 * leak (R8).
 */
final class TransactionLookup {

    private TransactionLookup() {
    }

    static Account ownedAccount(AccountRepository accounts, UUID workspaceId, UUID accountId) {
        if (accountId == null) {
            throw AppException.accountNotFound();
        }
        return accounts.findByIdAndWorkspaceId(accountId, workspaceId).orElseThrow(AppException::accountNotFound);
    }

    static Category ownedCategory(CategoryRepository categories, UUID workspaceId, UUID categoryId) {
        if (categoryId == null) {
            throw AppException.categoryNotFound();
        }
        return categories.findByIdAndWorkspaceId(categoryId, workspaceId).orElseThrow(AppException::categoryNotFound);
    }
}
