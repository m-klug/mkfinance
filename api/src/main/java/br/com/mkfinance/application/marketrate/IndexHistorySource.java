package br.com.mkfinance.application.marketrate;

import br.com.mkfinance.domain.model.IndexCode;
import java.time.LocalDate;
import java.util.List;

/** Where the daily history of the CDI and the Selic comes from. Tests swap in a fake. */
public interface IndexHistorySource {

    /**
     * Every published day of {@code code} from {@code from} to {@code to}, both inclusive. Days that
     * are not business days, or not published yet, are simply absent.
     *
     * @throws RuntimeException when the provider cannot be reached or answers something unreadable
     */
    List<IndexDailyQuote> between(IndexCode code, LocalDate from, LocalDate to);
}
