package com.nyle.kra.revenue.ml;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RiskScoringPrediction(
        UUID taxpayerId,
        BigDecimal modelScore,
        BigDecimal combinedScore,
        BigDecimal confidenceScore,
        List<String> mainContributingFeatures,
        Map<String, Object> explanation
) {
}
