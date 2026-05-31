package com.nyle.kra.revenue.ingestion;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "data_sources")
public class DataSource {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "owner_agency")
    private String ownerAgency;

    @Column(name = "ingestion_method", nullable = false)
    private String ingestionMethod;

    @Column(name = "schema_version")
    private String schemaVersion;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DataSource() {
    }

    public DataSource(
            String code,
            String name,
            String sourceType,
            String ownerAgency,
            String ingestionMethod,
            String schemaVersion,
            boolean active
    ) {
        this.id = UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.sourceType = sourceType;
        this.ownerAgency = ownerAgency;
        this.ingestionMethod = ingestionMethod;
        this.schemaVersion = schemaVersion;
        this.active = active;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getOwnerAgency() {
        return ownerAgency;
    }

    public String getIngestionMethod() {
        return ingestionMethod;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public boolean isActive() {
        return active;
    }
}
