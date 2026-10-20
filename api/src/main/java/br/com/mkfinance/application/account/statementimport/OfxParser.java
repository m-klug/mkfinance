package br.com.mkfinance.application.account.statementimport;

import br.com.mkfinance.application.account.statementimport.ParsedBankStatement.ParsedBankTransaction;
import br.com.mkfinance.domain.exception.AppException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads OFX 1.x (SGML) and 2.x (XML) statements with a tolerant tag scanner instead of an XML
 * parser: OFX 1.x leaf elements are often not closed, and the files declare a legacy charset.
 *
 * <p>Only what the import needs is read: the account identity, the period, the transactions and the
 * ledger balance. Nothing here is logged — the memos carry third parties' names.
 */
@Component
public class OfxParser implements BankStatementParser {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9._]*)>");
    private static final Pattern HEADER_CHARSET = Pattern.compile("(?im)^\\s*CHARSET\\s*:\\s*(\\S+)");
    private static final Pattern HEADER_ENCODING = Pattern.compile("(?im)^\\s*ENCODING\\s*:\\s*(\\S+)");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    @Override
    public ParsedBankStatement parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw AppException.ofxUnreadable();
        }
        String text = decode(content);
        int root = indexOfIgnoreCase(text, "<OFX>");
        if (root < 0) {
            throw AppException.ofxUnreadable();
        }

        Map<String, String> top = new HashMap<>();
        List<ParsedBankTransaction> transactions = new ArrayList<>();
        Map<String, String> current = null;
        String balance = null;
        int accounts = 0;

        Matcher matcher = TAG.matcher(text);
        matcher.region(root, text.length());
        while (matcher.find()) {
            boolean closing = !matcher.group(1).isEmpty();
            String tag = matcher.group(2).toUpperCase(Locale.ROOT);
            if (closing) {
                if (tag.equals("STMTTRN") && current != null) {
                    transactions.add(toTransaction(current));
                    current = null;
                }
                if (tag.equals("LEDGERBAL") || tag.equals("AVAILBAL")) {
                    balance = null;
                }
                continue;
            }
            if (tag.equals("STMTRS")) {
                accounts++;
            }
            if (tag.equals("STMTTRN")) {
                current = new HashMap<>();
                continue;
            }
            if (tag.equals("LEDGERBAL") || tag.equals("AVAILBAL")) {
                balance = tag;
                continue;
            }
            String value = valueAfter(text, matcher);
            if (value.isEmpty()) {
                continue;
            }
            if (balance != null) {
                // BALAMT/DTASOF appear under both LEDGERBAL and AVAILBAL; only the ledger one is used.
                if (balance.equals("LEDGERBAL")) {
                    top.putIfAbsent("LEDGER_" + tag, unescape(value));
                }
                continue;
            }
            (current != null ? current : top).putIfAbsent(tag, unescape(value));
        }
        if (current != null) {
            // An unterminated last transaction is still a transaction (SGML files may omit the close).
            transactions.add(toTransaction(current));
        }

        if (accounts > 1) {
            throw AppException.ofxUnsupported();
        }
        String currency = top.get("CURDEF");
        if (currency != null && !currency.equalsIgnoreCase("BRL")) {
            throw AppException.ofxUnsupported();
        }
        if (transactions.isEmpty()) {
            throw AppException.ofxUnreadable();
        }

        return new ParsedBankStatement(
                top.get("BANKID"),
                top.get("ACCTID"),
                optionalDate(top.get("DTSTART")),
                optionalDate(top.get("DTEND")),
                top.containsKey("LEDGER_BALAMT") ? cents(top.get("LEDGER_BALAMT")) : null,
                optionalDate(top.get("LEDGER_DTASOF")),
                transactions);
    }

    private static ParsedBankTransaction toTransaction(Map<String, String> fields) {
        String externalId = fields.get("FITID");
        String posted = fields.get("DTPOSTED");
        String amount = fields.get("TRNAMT");
        if (externalId == null || posted == null || amount == null) {
            throw AppException.ofxUnreadable();
        }
        // A zero amount is kept: the service warns about it and never imports it.
        return new ParsedBankTransaction(
                externalId, date(posted), cents(amount), fields.getOrDefault("MEMO", ""), fields.get("NAME"));
    }

    /** The text between this tag and the next one, trimmed — an unclosed SGML leaf's value. */
    private static String valueAfter(String text, Matcher matcher) {
        int start = matcher.end();
        int next = text.indexOf('<', start);
        return text.substring(start, next < 0 ? text.length() : next).strip();
    }

    private static long cents(String value) {
        try {
            return new BigDecimal(value.strip().replace(',', '.'))
                    .setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw AppException.ofxUnreadable();
        }
    }

    /** OFX dates are {@code yyyyMMdd[HHmmss[.XXX][gmt offset]]}; only the day matters here. */
    private static LocalDate date(String value) {
        if (value.length() < 8) {
            throw AppException.ofxUnreadable();
        }
        try {
            return LocalDate.parse(value.substring(0, 8), DATE);
        } catch (DateTimeParseException e) {
            throw AppException.ofxUnreadable();
        }
    }

    private static LocalDate optionalDate(String value) {
        return value == null ? null : date(value);
    }

    private static String decode(byte[] content) {
        String header = new String(content, 0, Math.min(content.length, 600), StandardCharsets.ISO_8859_1);
        if (declaresWindows1252(header)) {
            return new String(content, WINDOWS_1252);
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(content, WINDOWS_1252);
        }
    }

    private static boolean declaresWindows1252(String header) {
        Matcher charset = HEADER_CHARSET.matcher(header);
        if (charset.find() && charset.group(1).contains("1252")) {
            return true;
        }
        Matcher encoding = HEADER_ENCODING.matcher(header);
        return encoding.find() && encoding.group(1).equalsIgnoreCase("USASCII");
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        return text.toUpperCase(Locale.ROOT).indexOf(needle);
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
