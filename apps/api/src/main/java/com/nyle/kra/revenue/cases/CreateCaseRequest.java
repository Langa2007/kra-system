package com.nyle.kra.revenue.cases;

import java.util.UUID;

public record CreateCaseRequest(
        UUID riskSignalId,
        String title,
        String caseType,
        String priority,
        UUID assignedTo
) {
}
