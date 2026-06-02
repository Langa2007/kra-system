package com.nyle.kra.revenue.ml;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnalyticsRiskScoringResponse(
        @JsonProperty("model_name") String modelName,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("model_type") String modelType,
        String algorithm,
        @JsonProperty("training_data_summary") String trainingDataSummary,
        @JsonProperty("reproducibility_seed") int reproducibilitySeed,
        @JsonProperty("mlflow_run_id") String mlflowRunId,
        Map<String, Object> metrics,
        List<Prediction> predictions
) {
    public record Prediction(
            @JsonProperty("taxpayer_id") String taxpayerId,
            @JsonProperty("model_score") BigDecimal modelScore,
            @JsonProperty("combined_score") BigDecimal combinedScore,
            @JsonProperty("confidence_score") BigDecimal confidenceScore,
            @JsonProperty("main_contributing_features") List<String> mainContributingFeatures,
            Map<String, Object> explanation
    ) {
    }
}
