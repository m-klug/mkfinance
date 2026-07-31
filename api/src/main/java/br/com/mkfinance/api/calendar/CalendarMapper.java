package br.com.mkfinance.api.calendar;

import br.com.mkfinance.application.calendar.CalendarEntryView;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CalendarMapper {

    public CalendarMonthResponse toResponse(YearMonth month, List<CalendarEntryView> entries) {
        return new CalendarMonthResponse(month.toString(), entries.stream().map(this::toEntry).toList());
    }

    private CalendarEntryResponse toEntry(CalendarEntryView view) {
        return new CalendarEntryResponse(
                view.date(),
                view.kind(),
                view.description(),
                view.amountCents(),
                view.direction(),
                view.transactionId(),
                view.statementId(),
                view.cardId(),
                view.accountId(),
                view.categoryId(),
                view.recurrenceId());
    }
}
