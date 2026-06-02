package com.nyle.kra.revenue.reports;

import java.math.BigDecimal;

public record OfficerProductivityResponse(
        String officerId,
        String officerName,
        long assignedCases,
        long openCases,
        long closedCases,
        BigDecimal assessedAmount,
        BigDecimal agreedAmount,
        BigDecimal collectedAmount,
        BigDecimal averageCaseAgeDays
) {
}

