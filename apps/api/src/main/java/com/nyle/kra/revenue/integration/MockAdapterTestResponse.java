package com.nyle.kra.revenue.integration;

import java.util.List;
import java.util.Map;

public record MockAdapterTestResponse(
        String adapterType,
        String status,
        boolean reachable,
        boolean secretsRedacted,
        int retryCount,
        int maxRetries,
        Map<String, Object> sanitizedConnectionProfile,
        List<String> checks
) {
}
