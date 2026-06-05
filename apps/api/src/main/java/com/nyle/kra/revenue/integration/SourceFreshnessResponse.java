package com.nyle.kra.revenue.integration;

import java.time.OffsetDateTime;

public record SourceFreshnessResponse(
        String dataSourceCode,
        String dataSourceName,
        String ownerAgency,
        String ingestionMethod,
        String integrationStatus,
        OffsetDateTime lastSuccessfulIngestionAt,
        int expectedFreshnessMinutes,
        long minutesSinceLastSuccess,
        boolean late,
        String alertLevel
) {
}
