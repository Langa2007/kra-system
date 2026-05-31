package com.nyle.kra.revenue.ingestion;

import jakarta.validation.constraints.NotBlank;

public record CreateDataSourceRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String sourceType,
        String ownerAgency,
        @NotBlank String ingestionMethod,
        String schemaVersion,
        Boolean active
) {
}
