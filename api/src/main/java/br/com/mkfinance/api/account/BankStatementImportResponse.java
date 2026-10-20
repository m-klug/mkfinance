package br.com.mkfinance.api.account;

/** Contract: {@code BankStatementImportResult}. */
public record BankStatementImportResponse(int entries, int transfers, int statementPayments) {
}
