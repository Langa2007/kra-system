package com.nyle.kra.revenue.graph;

import java.util.List;
import java.util.UUID;

public record TaxpayerGraphResponse(
        UUID taxpayerId,
        List<GraphNodeResponse> nodes,
        List<GraphEdgeResponse> edges,
        List<HighRiskClusterResponse> highRiskClusters
) {
}
