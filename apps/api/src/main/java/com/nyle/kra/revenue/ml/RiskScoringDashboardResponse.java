package com.nyle.kra.revenue.ml;

import java.math.BigDecimal;
import java.util.List;

public record RiskScoringDashboardResponse(
        String activeModelVersion,
        int predictionCount,
        int highRiskCount,
        BigDecimal averageModelScore,
        BigDecimal averageCombinedScore,
        List<ModelPredictionResponse> topPredictions
) {
}
