package com.nyle.kra.revenue.integration;

import java.util.List;

public record GovernmentIntegrationReadinessResponse(
        String phase,
        int dataSources,
        int activeSources,
        int schemaMappingCount,
        int lateSources,
        int controlledRetryErrors,
        List<AdapterTemplateResponse> adapterTemplates,
        List<SourceSchemaMappingResponse> schemaMappings,
        List<SourceFreshnessResponse> freshness,
        List<IntegrationErrorResponse> integrationErrors,
        List<String> securityControls,
        List<String> dataProcessingAgreementSections
) {
}
