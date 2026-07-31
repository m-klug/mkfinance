package br.com.mkfinance.api.calendar;

import java.util.List;

/** Contract schema {@code CalendarMonth}. */
public record CalendarMonthResponse(String month, List<CalendarEntryResponse> entries) {
}
