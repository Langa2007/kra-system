package com.nyle.kra.revenue.ingestion;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "data_source_id", nullable = false)
    private DataSource dataSource;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "target_table")
    private String targetTable;

    @Column(name = "file_sha256")
    private String fileSha256;

    @Column(nullable = false)
    private String status;

    @Column(name = "records_received", nullable = false)
    private long recordsReceived;

    @Column(name = "records_valid", nullable = false)
    private long recordsValid;

    @Column(name = "records_invalid", nullable = false)
    private long recordsInvalid;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "error_summary")
    private String errorSummary;

    @Column(name = "created_by")
    private UUID createdBy;

    protected IngestionJob() {
    }

    public IngestionJob(DataSource dataSource, String fileName, String targetTable, String fileSha256, UUID createdBy) {
        this.id = UUID.randomUUID();
        this.dataSource = dataSource;
        this.fileName = fileName;
        this.targetTable = targetTable;
        this.fileSha256 = fileSha256;
        this.createdBy = createdBy;
        this.status = "RUNNING";
        this.startedAt = OffsetDateTime.now();
    }

    public void complete(long recordsReceived, long recordsValid, long recordsInvalid, String errorSummary) {
        this.recordsReceived = recordsReceived;
        this.recordsValid = recordsValid;
        this.recordsInvalid = recordsInvalid;
        this.errorSummary = errorSummary;
        this.status = recordsInvalid == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS";
        this.completedAt = OffsetDateTime.now();
    }

    public void duplicateOf(UUID originalJobId) {
        this.status = "DUPLICATE";
        this.errorSummary = "Duplicate upload. Original ingestion job: " + originalJobId;
        this.completedAt = OffsetDateTime.now();
    }

    public void fail(String errorSummary) {
        this.status = "FAILED";
        this.errorSummary = errorSummary;
        this.completedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public String getFileName() {
        return fileName;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public String getStatus() {
        return status;
    }

    public long getRecordsReceived() {
        return recordsReceived;
    }

    public long getRecordsValid() {
        return recordsValid;
    }

    public long getRecordsInvalid() {
        return recordsInvalid;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public String getErrorSummary() {
        return errorSummary;
    }
}
