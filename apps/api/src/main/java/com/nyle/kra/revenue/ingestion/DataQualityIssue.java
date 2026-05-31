package com.nyle.kra.revenue.ingestion;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "data_quality_issues")
public class DataQualityIssue {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "ingestion_job_id")
    private IngestionJob ingestionJob;

    @ManyToOne
    @JoinColumn(name = "data_source_id")
    private DataSource dataSource;

    @Column(nullable = false)
    private String severity;

    @Column(name = "issue_type", nullable = false)
    private String issueType;

    @Column(name = "record_reference")
    private String recordReference;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "issue_message", nullable = false)
    private String issueMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "record_payload", columnDefinition = "jsonb")
    private Map<String, Object> recordPayload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DataQualityIssue() {
    }

    public DataQualityIssue(
            IngestionJob ingestionJob,
            DataSource dataSource,
            String severity,
            String issueType,
            String recordReference,
            String fieldName,
            String issueMessage,
            Map<String, Object> recordPayload
    ) {
        this.id = UUID.randomUUID();
        this.ingestionJob = ingestionJob;
        this.dataSource = dataSource;
        this.severity = severity;
        this.issueType = issueType;
        this.recordReference = recordReference;
        this.fieldName = fieldName;
        this.issueMessage = issueMessage;
        this.recordPayload = recordPayload;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getSeverity() {
        return severity;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getRecordReference() {
        return recordReference;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getIssueMessage() {
        return issueMessage;
    }
}
