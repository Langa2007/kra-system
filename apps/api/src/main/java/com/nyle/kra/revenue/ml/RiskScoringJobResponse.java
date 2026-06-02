package com.nyle.kra.revenue.ml;

import java.util.UUID;

public record RiskScoringJobResponse(
        UUID modelVersionId,
        String modelName,
        String modelVersion,
        int taxpayersScored,
        int predictionsCreated,
        int combinedScoresCreated
) {
}
