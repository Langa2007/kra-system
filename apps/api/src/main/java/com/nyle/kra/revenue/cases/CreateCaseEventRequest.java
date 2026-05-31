package com.nyle.kra.revenue.cases;

public record CreateCaseEventRequest(
        String eventType,
        String eventNote
) {
}
