package com.nyle.kra.revenue.integration;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record MockAdapterTestRequest(
        @NotBlank String adapterType,
        Map<String, Object> connectionProfile
) {
}
