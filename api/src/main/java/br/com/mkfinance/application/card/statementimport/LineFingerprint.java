package br.com.mkfinance.application.card.statementimport;

import br.com.mkfinance.application.card.statementimport.ParsedStatement.ParsedLine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Identity of one statement line, derived from the line itself because the Inter PDF carries no id.
 *
 * <p>Built from the bank's own text (never the user's description), so reading the same bill again
 * gives the same value. {@code occurrence} tells apart lines identical in every other field (two
 * coffees on the same day): it is the position of the line among its identical siblings in the file.
 */
final class LineFingerprint {

    static final String PREFIX = "fp:";

    private LineFingerprint() {
    }

    static String of(String cardId, ParsedLine line, int occurrence) {
        String raw = String.join("|",
                cardId,
                line.cardLast4(),
                line.date().toString(),
                Long.toString(line.amountCents()),
                normalize(line.description()),
                String.valueOf(line.installmentNumber()),
                String.valueOf(line.installmentCount()),
                Integer.toString(occurrence));
        return PREFIX + sha256(raw).substring(0, 40);
    }

    /** Lowercase, no accents, single spaces: survives cosmetic changes in how the PDF breaks lines. */
    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
