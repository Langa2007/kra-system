package com.nyle.kra.revenue.ingestion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    List<IngestionJob> findAllByOrderByStartedAtDesc();

    Optional<IngestionJob> findFirstByDataSourceIdAndTargetTableAndFileSha256AndStatusInOrderByStartedAtAsc(
            UUID dataSourceId,
            String targetTable,
            String fileSha256,
            List<String> statuses
    );
}
