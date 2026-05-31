package com.nyle.kra.revenue.ingestion;

import java.util.UUID;

public record DataQualityIssueResponse(
        UUID id,
        String severity,
        String issueType,
        String recordReference,
        String fieldName,
        String issueMessage
) {
    static DataQualityIssueResponse from(DataQualityIssue issue) {
        return new DataQualityIssueResponse(
                issue.getId(),
                issue.getSeverity(),
                issue.getIssueType(),
                issue.getRecordReference(),
                issue.getFieldName(),
                issue.getIssueMessage()
        );
    }
}
