package com.nyle.kra.revenue.ml;

import java.util.List;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsRiskScoringClient {

    private static final int REPRODUCIBILITY_SEED = 42;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String riskScoringUrl;

    public AnalyticsRiskScoringClient(
            ObjectMapper objectMapper,
            @Value("${app.analytics.risk-scoring-url:http://localhost:8000/risk-scoring/train}")
            String riskScoringUrl
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.riskScoringUrl = riskScoringUrl;
    }

    public AnalyticsRiskScoringResponse trainAndScore(List<TaxpayerRiskFeature> features) {
        AnalyticsRiskScoringRequest request = new AnalyticsRiskScoringRequest(
                RiskScoringConstants.MODEL_NAME,
                REPRODUCIBILITY_SEED,
                features.stream().map(this::observation).toList()
        );
        String jsonPayload = json(request);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(riskScoringUrl))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonPayload.getBytes(StandardCharsets.UTF_8)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "Analytics risk scoring failed with HTTP " + response.statusCode()
                                + " for payload length " + jsonPayload.length()
                                + ": " + response.body()
                );
            }
            return objectMapper.readValue(response.body(), AnalyticsRiskScoringResponse.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not call analytics risk scoring service", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Analytics risk scoring call was interrupted", ex);
        }
    }

    private String json(AnalyticsRiskScoringRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize analytics risk scoring request", ex);
        }
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
