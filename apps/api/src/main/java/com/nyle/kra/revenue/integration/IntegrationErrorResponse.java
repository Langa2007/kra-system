package com.nyle.kra.revenue.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IntegrationErrorResponse(
        UUID jobId,
        String dataSourceCode,
        String fileName,
        String status,
        int retryCount,
        int maxRetries,
        OffsetDateTime nextRetryAt,
        String errorSummary,
        boolean retryControlled
) {
}
