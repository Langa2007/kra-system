package com.nyle.kra.revenue.integration;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateSourceSchemaMappingRequest(
        @NotNull UUID dataSourceId,
        @NotEmpty Map<String, Object> sourceSchema,
        @NotBlank String targetEntity,
        @NotEmpty Map<String, Object> mappingConfig,
        Boolean active
) {
}
