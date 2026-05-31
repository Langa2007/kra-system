package com.nyle.kra.revenue.ingestion;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IngestionJobResponse(
        UUID id,
        UUID dataSourceId,
        String dataSourceCode,
        String fileName,
        String targetTable,
        String fileSha256,
        String status,
        long recordsReceived,
        long recordsValid,
        long recordsInvalid,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String errorSummary
) {
    static IngestionJobResponse from(IngestionJob job) {
        return new IngestionJobResponse(
                job.getId(),
                job.getDataSource().getId(),
                job.getDataSource().getCode(),
                job.getFileName(),
                job.getTargetTable(),
                job.getFileSha256(),
                job.getStatus(),
                job.getRecordsReceived(),
                job.getRecordsValid(),
                job.getRecordsInvalid(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getErrorSummary()
        );
    }
}
