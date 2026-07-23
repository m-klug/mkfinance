package br.com.mkfinance.api.export;

import br.com.mkfinance.application.export.TransactionCsvExporter;
import br.com.mkfinance.infrastructure.security.WorkspaceContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract tag {@code export}. Streams the extrato as a CSV download; the same {@code from}/{@code to}
 * window as the extrato listing selects the rows. Queries run through
 * {@link WorkspaceContext#getWorkspaceId()}, already verified by {@code WorkspaceAccessInterceptor}.
 */
@RestController
@RequestMapping("/workspaces/{workspaceId}/export")
@RequiredArgsConstructor
public class ExportController {

    private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final TransactionCsvExporter exporter;
    private final WorkspaceContext workspaceContext;

    @GetMapping("/transactions.csv")
    public ResponseEntity<byte[]> exportTransactions(
            @PathVariable UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] csv = exporter.export(workspaceContext.getWorkspaceId(), from, to);
        return ResponseEntity.ok()
                .contentType(TEXT_CSV_UTF8)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions.csv\"")
                .body(csv);
    }
}
