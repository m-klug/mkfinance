package br.com.mkfinance.application.card.statementimport;

import br.com.mkfinance.domain.exception.AppException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Opens a (possibly password-protected) statement PDF and returns its text.
 *
 * <p>The password lives only as a method argument: it is never stored, logged, or put into an
 * exception message. Nothing in here prints the arguments, and the failures it raises carry only a
 * machine code.
 */
@Component
public class StatementPdfReader {

    public String readText(byte[] pdf, String password) {
        try (PDDocument document = Loader.loadPDF(pdf, password == null ? "" : password)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw AppException.pdfPasswordInvalid();
        } catch (IOException e) {
            throw AppException.pdfUnreadable();
        }
    }
}
