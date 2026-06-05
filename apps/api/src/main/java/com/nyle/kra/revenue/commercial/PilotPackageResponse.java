package com.nyle.kra.revenue.commercial;

import java.util.List;

public record PilotPackageResponse(
        String phase,
        String buyerReadiness,
        String pilotObjective,
        RoiSummaryResponse roi,
        List<PilotDocumentResponse> documents,
        List<DemoUserPersonaResponse> demoUsers,
        List<String> sampleDashboards,
        List<String> procurementRoutes,
        String dataProcessingOverview,
        String deploymentOverview,
        String pricingModel
) {
}
