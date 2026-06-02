package com.nyle.kra.revenue.ml;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnalyticsRiskScoringRequest(
        @JsonProperty("model_name") String modelName,
        int seed,
        List<Observation> observations
) {
    public record Observation(
            @JsonProperty("taxpayer_id") String taxpayerId,
            @JsonProperty("sector_name") String sectorName,
            @JsonProperty("declared_sales") BigDecimal declaredSales,
            @JsonProperty("declared_income") BigDecimal declaredIncome,
            @JsonProperty("invoice_sales") BigDecimal invoiceSales,
            @JsonProperty("customs_landed_cost") BigDecimal customsLandedCost,
            @JsonProperty("withholding_income") BigDecimal withholdingIncome,
            @JsonProperty("risk_signal_gap") BigDecimal riskSignalGap,
            @JsonProperty("rule_score") BigDecimal ruleScore,
            @JsonProperty("return_count") int returnCount,
            @JsonProperty("invoice_count") int invoiceCount,
            @JsonProperty("customs_count") int customsCount,
            @JsonProperty("withholding_count") int withholdingCount,
            @JsonProperty("open_signal_count") int openSignalCount
    ) {
    }
}
