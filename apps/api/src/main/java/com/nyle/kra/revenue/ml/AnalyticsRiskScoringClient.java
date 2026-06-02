package com.nyle.kra.revenue.ml;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AnalyticsRiskScoringClient {

    private static final int REPRODUCIBILITY_SEED = 42;

    private final RestClient restClient;
    private final String riskScoringUrl;

    public AnalyticsRiskScoringClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.analytics.risk-scoring-url:http://localhost:8000/risk-scoring/train}")
            String riskScoringUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.riskScoringUrl = riskScoringUrl;
    }

    public AnalyticsRiskScoringResponse trainAndScore(List<TaxpayerRiskFeature> features) {
        AnalyticsRiskScoringRequest request = new AnalyticsRiskScoringRequest(
                RiskScoringConstants.MODEL_NAME,
                REPRODUCIBILITY_SEED,
                features.stream().map(this::observation).toList()
        );
        return restClient.post()
                .uri(riskScoringUrl)
                .body(request)
                .retrieve()
                .body(AnalyticsRiskScoringResponse.class);
    }

    private AnalyticsRiskScoringRequest.Observation observation(TaxpayerRiskFeature feature) {
        return new AnalyticsRiskScoringRequest.Observation(
                feature.taxpayerId().toString(),
                feature.sectorName(),
                feature.declaredSales(),
                feature.declaredIncome(),
                feature.invoiceSales(),
                feature.customsLandedCost(),
                feature.withholdingIncome(),
                feature.riskSignalGap(),
                feature.ruleScore(),
                feature.returnCount(),
                feature.invoiceCount(),
                feature.customsCount(),
                feature.withholdingCount(),
                feature.openSignalCount()
        );
    }
}
