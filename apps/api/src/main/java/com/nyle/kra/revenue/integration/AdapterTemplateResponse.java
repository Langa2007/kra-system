package com.nyle.kra.revenue.integration;

import java.util.List;
import java.util.Map;

public record AdapterTemplateResponse(
        String adapterType,
        String label,
        String approvedChannel,
        String ingestionMethod,
        List<String> requiredControls,
        Map<String, Object> connectionTemplate
) {
}
