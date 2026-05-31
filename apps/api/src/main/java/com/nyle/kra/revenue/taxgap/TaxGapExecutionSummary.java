package com.nyle.kra.revenue.taxgap;

public record TaxGapExecutionSummary(
        int estimatesTouched,
        int taxpayerScoresCreated,
        int sourceSignalsUsed
) {
}
