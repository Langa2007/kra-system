package com.nyle.kra.revenue.cases;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateCaseRequest(
        String priority,
        String status,
        UUID assignedTo,
        String closureReason,
        BigDecimal assessedAmount,
        BigDecimal agreedAmount,
        BigDecimal collectedAmount,
        LocalDate collectionDate,
        String recoveryStatus
) {
}
