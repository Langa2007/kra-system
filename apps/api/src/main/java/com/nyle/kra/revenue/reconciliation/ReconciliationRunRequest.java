package com.nyle.kra.revenue.reconciliation;

import java.time.LocalDate;

public record ReconciliationRunRequest(
        LocalDate from,
        LocalDate to,
        Integer settlementDelayDays,
        String expectedSettlementAccount
) {
}
