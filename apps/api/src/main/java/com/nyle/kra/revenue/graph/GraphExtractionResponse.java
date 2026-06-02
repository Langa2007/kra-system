package com.nyle.kra.revenue.graph;

public record GraphExtractionResponse(
        int taxpayerRelationshipEdges,
        int invoiceTradeEdges,
        int withholdingFlowEdges,
        int sharedIdentifierEdges,
        int permitEdges,
        int paymentChannelEdges,
        int importActivityEdges,
        int highRiskClustersDetected
) {

    public int edgesTouched() {
        return taxpayerRelationshipEdges
                + invoiceTradeEdges
                + withholdingFlowEdges
                + sharedIdentifierEdges
                + permitEdges
                + paymentChannelEdges
                + importActivityEdges;
    }
}
