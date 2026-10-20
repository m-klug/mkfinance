package br.com.mkfinance.application.account.statementimport;

/** Reads a bank's statement file into neutral data. One implementation per file format. */
public interface BankStatementParser {

    /**
     * @throws br.com.mkfinance.domain.exception.AppException OFX_UNREADABLE when the content is not
     *     a statement with transactions, OFX_UNSUPPORTED when it is one the importer does not handle
     */
    ParsedBankStatement parse(byte[] content);
}
