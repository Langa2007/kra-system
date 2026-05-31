package com.nyle.kra.revenue.taxgap;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TaxGapRuleConfig(
        @JsonProperty("signal_type")
        String signalType,
        @JsonProperty("configured_tax_head")
        String taxHead,
        @JsonProperty("recoverable_rate")
        BigDecimal recoverableRate,
        @JsonProperty("penalty_rate")
        BigDecimal penaltyRate,
        @JsonProperty("interest_rate")
        BigDecimal interestRate,
        String basis
) {
}
