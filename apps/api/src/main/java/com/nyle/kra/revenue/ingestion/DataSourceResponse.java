package com.nyle.kra.revenue.ingestion;

import java.util.UUID;

public record DataSourceResponse(
        UUID id,
        String code,
        String name,
        String sourceType,
        String ownerAgency,
        String ingestionMethod,
        String schemaVersion,
        boolean active
) {
    static DataSourceResponse from(DataSource dataSource) {
        return new DataSourceResponse(
                dataSource.getId(),
                dataSource.getCode(),
                dataSource.getName(),
                dataSource.getSourceType(),
                dataSource.getOwnerAgency(),
                dataSource.getIngestionMethod(),
                dataSource.getSchemaVersion(),
                dataSource.isActive()
        );
    }
}
