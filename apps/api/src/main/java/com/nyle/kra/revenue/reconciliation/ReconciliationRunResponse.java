package com.nyle.kra.revenue.reconciliation;

import java.time.LocalDate;

public record ReconciliationRunResponse(
        LocalDate from,
        LocalDate to,
        int resultsTouched,
        int exceptions,
        int riskSignalsTouched
) {
}
