package br.com.mkfinance.application.account.statementimport;

/** How many records a confirmed import created, per mode. */
public record BankStatementImportResult(int entries, int transfers, int statementPayments) {
}
