package br.com.mkfinance.application.card.statementimport;

/** Reads the extracted text of one bank's card statement. One implementation per bank layout. */
public interface StatementParser {

    /**
     * @throws br.com.mkfinance.domain.exception.AppException {@code STATEMENT_PDF_UNSUPPORTED} when
     *     the text is not a statement this parser understands
     */
    ParsedStatement parse(String text);
}
