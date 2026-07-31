package br.com.mkfinance.api.recurrence;

import br.com.mkfinance.application.recurrence.RecurrenceRuleInput;
import br.com.mkfinance.domain.model.RecurrenceRule;
import org.springframework.stereotype.Component;

@Component
public class RecurrenceMapper {

    public RecurrenceRuleInput toInput(RecurrenceRequest request) {
        return new RecurrenceRuleInput(
                request.type(),
                request.accountId(),
                request.categoryId(),
                request.amountCents(),
                request.description(),
                request.note(),
                request.frequency(),
                request.dayOfMonth(),
                request.dayOfWeek(),
                request.monthOfYear(),
                request.startDate(),
                request.endDate(),
                request.active());
    }

    public RecurrenceResponse toResponse(RecurrenceRule rule) {
        return new RecurrenceResponse(
                rule.getId(),
                rule.getType(),
                rule.getAccountId(),
                rule.getCategoryId(),
                rule.getAmountCents(),
                rule.getDescription(),
                rule.getNote(),
                rule.getFrequency(),
                rule.getDayOfMonth(),
                rule.getDayOfWeek(),
                rule.getMonthOfYear(),
                rule.getStartDate(),
                rule.getEndDate(),
                rule.isActive());
    }
}
