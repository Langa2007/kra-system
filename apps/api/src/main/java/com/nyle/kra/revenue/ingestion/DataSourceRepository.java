package com.nyle.kra.revenue.ingestion;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSourceRepository extends JpaRepository<DataSource, UUID> {

    Optional<DataSource> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
