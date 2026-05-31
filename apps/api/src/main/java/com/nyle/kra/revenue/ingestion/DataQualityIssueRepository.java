package com.nyle.kra.revenue.ingestion;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DataQualityIssueRepository extends JpaRepository<DataQualityIssue, UUID> {

    List<DataQualityIssue> findByIngestionJobIdOrderByCreatedAtAsc(UUID ingestionJobId);

    long countByIngestionJobId(UUID ingestionJobId);
}
