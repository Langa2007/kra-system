package com.nyle.kra.revenue.ml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class AnalyticsRiskScoringClientTest {

    @Test
    void sendsJsonRequestBodyToAnalyticsService() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/risk-scoring/train", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "model_name": "PHASE12_UNSUPERVISED_RISK_SCORING",
                      "model_version": "phase12-test",
                      "model_type": "UNSUPERVISED_ANOMALY",
                      "algorithm": "ISOLATION_FOREST_WITH_PEER_PERCENTILE",
                      "training_data_summary": "test",
                      "reproducibility_seed": 42,
                      "mlflow_run_id": null,
                      "metrics": {"predictionCount": 0},
                      "predictions": []
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            String url = "http://localhost:%d/risk-scoring/train".formatted(server.getAddress().getPort());
            AnalyticsRiskScoringClient client = new AnalyticsRiskScoringClient(new ObjectMapper(), url);

            AnalyticsRiskScoringResponse response = client.trainAndScore(List.of(feature()));

            assertThat(response.modelVersion()).isEqualTo("phase12-test");
            assertThat(requestBody.get()).contains("\"model_name\"");
            assertThat(requestBody.get()).contains("\"observations\":[");
            assertThat(requestBody.get()).contains("\"taxpayer_id\":\"11111111-1111-4111-8111-111111111111\"");
            assertThat(requestBody.get()).contains("\"risk_signal_gap\":90000");
        } finally {
            server.stop(0);
        }
    }

    private TaxpayerRiskFeature feature() {
        return new TaxpayerRiskFeature(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "P000001",
                "Test Taxpayer",
                null,
                "Nairobi",
                LocalDate.now().minusDays(30),
                LocalDate.now(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("90000.00"),
                BigDecimal.ZERO,
                0,
                0,
                0,
                0,
                1
        );
    }
}
