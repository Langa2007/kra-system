package com.nyle.kra.revenue.integration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyle.kra.revenue.ingestion.DataSourceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class GovernmentIntegrationReadinessService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Set<String> SECRET_KEYS = Set.of(
            "password",
            "secret",
            "clientSecret",
            "client_secret",
            "token",
            "apiKey",
            "privateKey",
            "passphrase"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DataSourceRepository dataSourceRepository;

    public GovernmentIntegrationReadinessService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            DataSourceRepository dataSourceRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dataSourceRepository = dataSourceRepository;
    }

    public GovernmentIntegrationReadinessResponse dashboard() {
        List<SourceSchemaMappingResponse> mappings = listMappings();
        List<SourceFreshnessResponse> freshness = sourceFreshness();
        List<IntegrationErrorResponse> errors = integrationErrors();
        Integer dataSources = jdbcTemplate.queryForObject("SELECT count(*) FROM data_sources", Integer.class);
        Integer activeSources = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM data_sources WHERE active = TRUE",
                Integer.class
        );

        return new GovernmentIntegrationReadinessResponse(
                "Phase 17",
                value(dataSources),
                value(activeSources),
                mappings.size(),
                (int) freshness.stream().filter(SourceFreshnessResponse::late).count(),
                (int) errors.stream().filter(IntegrationErrorResponse::retryControlled).count(),
                adapterTemplates(),
                mappings,
                freshness,
                errors,
                securityControls(),
                dataProcessingAgreementSections()
        );
    }

    public List<AdapterTemplateResponse> adapterTemplates() {
        return List.of(
                new AdapterTemplateResponse(
                        "API",
                        "Government REST API",
                        "Mutual TLS or signed OAuth client",
                        "API_POLL",
                        List.of("TLS 1.2+", "Credential vault reference", "Request correlation ID", "No secrets in logs"),
                        Map.of(
                                "baseUrl", "https://agency.example.gov/api",
                                "auth", Map.of("type", "OAUTH2_CLIENT_CREDENTIALS", "clientSecret", "***"),
                                "polling", Map.of("intervalMinutes", 60, "maxRetries", 3)
                        )
                ),
                new AdapterTemplateResponse(
                        "SFTP",
                        "Approved SFTP Drop",
                        "Whitelisted SFTP endpoint with key-based auth",
                        "SFTP_BATCH",
                        List.of("Host key pinning", "Private key vault reference", "Raw file archive", "Checksum validation"),
                        Map.of(
                                "host", "sftp.agency.example.gov",
                                "port", 22,
                                "remotePath", "/outbound/revenue",
                                "privateKey", "***",
                                "archiveBucket", "phase17-raw-government-files"
                        )
                ),
                new AdapterTemplateResponse(
                        "DATABASE",
                        "Read-only Database Connector",
                        "Least-privilege read-only account over private network",
                        "READ_ONLY_DB",
                        List.of("Read-only grants", "Query allow-list", "Network allow-list", "No write credentials"),
                        Map.of(
                                "jdbcUrl", "jdbc:postgresql://readonly.agency.example.gov:5432/source",
                                "username", "readonly_revenue_user",
                                "password", "***",
                                "queryAllowList", List.of("taxpayer_registry_snapshot", "payment_collections_snapshot")
                        )
                )
        );
    }

    public List<SourceSchemaMappingResponse> listMappings() {
        return jdbcTemplate.query("""
                SELECT ssm.id, ssm.data_source_id, ds.code AS data_source_code, ssm.target_entity,
                       ssm.source_schema::text AS source_schema, ssm.mapping_config::text AS mapping_config,
                       ssm.active, ssm.created_at, ssm.updated_at
                FROM source_schema_mappings ssm
                JOIN data_sources ds ON ds.id = ssm.data_source_id
                ORDER BY ssm.created_at DESC, ssm.target_entity
                """, this::mapSchemaMapping);
    }

    @Transactional
    public SourceSchemaMappingResponse createMapping(CreateSourceSchemaMappingRequest request) {
        if (!dataSourceRepository.existsById(request.dataSourceId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Data source not found");
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO source_schema_mappings (
                    id, data_source_id, source_schema, target_entity, mapping_config, active, created_at, updated_at
                )
                VALUES (?, ?, CAST(? AS jsonb), ?, CAST(? AS jsonb), ?, now(), now())
                """,
                id,
                request.dataSourceId(),
                toJson(request.sourceSchema()),
                request.targetEntity(),
                toJson(request.mappingConfig()),
                request.active() == null || request.active()
        );

        return jdbcTemplate.queryForObject("""
                SELECT ssm.id, ssm.data_source_id, ds.code AS data_source_code, ssm.target_entity,
                       ssm.source_schema::text AS source_schema, ssm.mapping_config::text AS mapping_config,
                       ssm.active, ssm.created_at, ssm.updated_at
                FROM source_schema_mappings ssm
                JOIN data_sources ds ON ds.id = ssm.data_source_id
                WHERE ssm.id = ?
                """, this::mapSchemaMapping, id);
    }

    public List<SourceFreshnessResponse> sourceFreshness() {
        OffsetDateTime now = OffsetDateTime.now();
        return jdbcTemplate.query("""
                SELECT ds.code, ds.name, ds.owner_agency, ds.ingestion_method, ds.integration_status,
                       ds.expected_freshness_minutes,
                       coalesce(ds.last_successful_ingestion_at, max(ij.completed_at)) AS last_successful_ingestion_at
                FROM data_sources ds
                LEFT JOIN ingestion_jobs ij ON ij.data_source_id = ds.id AND ij.status = 'COMPLETED'
                WHERE ds.active = TRUE
                GROUP BY ds.id
                ORDER BY ds.code
                """, (rs, rowNum) -> {
            OffsetDateTime lastSuccess = rs.getObject("last_successful_ingestion_at", OffsetDateTime.class);
            int expectedMinutes = rs.getInt("expected_freshness_minutes");
            long minutesSinceLastSuccess = lastSuccess == null
                    ? expectedMinutes + 1L
                    : Math.max(0, Duration.between(lastSuccess, now).toMinutes());
            boolean late = minutesSinceLastSuccess > expectedMinutes;
            String alertLevel = late ? "LATE" : "CURRENT";
            return new SourceFreshnessResponse(
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("owner_agency"),
                    rs.getString("ingestion_method"),
                    rs.getString("integration_status"),
                    lastSuccess,
                    expectedMinutes,
                    minutesSinceLastSuccess,
                    late,
                    alertLevel
            );
        });
    }

    public List<IntegrationErrorResponse> integrationErrors() {
        return jdbcTemplate.query("""
                SELECT ij.id, ds.code AS data_source_code, ij.file_name, ij.status, ij.retry_count,
                       ij.max_retries, ij.next_retry_at, ij.error_summary
                FROM ingestion_jobs ij
                JOIN data_sources ds ON ds.id = ij.data_source_id
                WHERE ij.status IN ('FAILED', 'RETRY_SCHEDULED')
                ORDER BY ij.started_at DESC
                LIMIT 25
                """, (rs, rowNum) -> new IntegrationErrorResponse(
                rs.getObject("id", UUID.class),
                rs.getString("data_source_code"),
                rs.getString("file_name"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getInt("max_retries"),
                rs.getObject("next_retry_at", OffsetDateTime.class),
                rs.getString("error_summary"),
                rs.getInt("retry_count") <= rs.getInt("max_retries")
        ));
    }

    public MockAdapterTestResponse testAdapter(MockAdapterTestRequest request) {
        String adapterType = request.adapterType().toUpperCase();
        if (!Set.of("API", "SFTP", "DATABASE").contains(adapterType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported adapter type");
        }

        Map<String, Object> sanitizedProfile = sanitize(request.connectionProfile() == null
                ? Map.of()
                : request.connectionProfile());
        List<String> checks = new ArrayList<>();
        checks.add("Adapter type is approved for Phase 17");
        checks.add("Connection profile was sanitized before response or audit");
        checks.add(sanitizedProfile.isEmpty()
                ? "Template-only dry run completed"
                : "Mock connectivity check completed");
        checks.add("Retry policy capped at 3 attempts");

        return new MockAdapterTestResponse(
                adapterType,
                "READY",
                true,
                true,
                0,
                3,
                sanitizedProfile,
                checks
        );
    }

    public Map<String, Object> sanitize(Map<String, Object> value) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            Object entryValue = entry.getValue();
            if (isSecretKey(entry.getKey())) {
                sanitized.put(entry.getKey(), "***");
            } else if (entryValue instanceof Map<?, ?> nested) {
                sanitized.put(entry.getKey(), sanitizeNested(nested));
            } else {
                sanitized.put(entry.getKey(), entryValue);
            }
        }
        return sanitized;
    }

    private Map<String, Object> sanitizeNested(Map<?, ?> value) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object entryValue = entry.getValue();
            if (isSecretKey(key)) {
                sanitized.put(key, "***");
            } else if (entryValue instanceof Map<?, ?> nested) {
                sanitized.put(key, sanitizeNested(nested));
            } else {
                sanitized.put(key, entryValue);
            }
        }
        return sanitized;
    }

    private boolean isSecretKey(String key) {
        return SECRET_KEYS.contains(key) || key.toLowerCase().contains("password");
    }

    private SourceSchemaMappingResponse mapSchemaMapping(ResultSet rs, int rowNum) throws SQLException {
        return new SourceSchemaMappingResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("data_source_id", UUID.class),
                rs.getString("data_source_code"),
                rs.getString("target_entity"),
                fromJson(rs.getString("source_schema")),
                fromJson(rs.getString("mapping_config")),
                rs.getBoolean("active"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not parse schema mapping JSON", exception);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize schema mapping JSON", exception);
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private List<String> securityControls() {
        return List.of(
                "Secrets are supplied by vault references and redacted from responses.",
                "SFTP templates require host key pinning and checksum validation.",
                "Database templates require read-only accounts and query allow-lists.",
                "API templates require signed clients, correlation IDs, and TLS.",
                "Retry policy is capped and observable through integration error dashboards."
        );
    }

    private List<String> dataProcessingAgreementSections() {
        return List.of(
                "Source owner and lawful processing purpose",
                "Permitted data categories and minimization controls",
                "Retention, raw archive, and deletion responsibilities",
                "Incident notification and audit access",
                "Pilot boundary: no enforcement action without authorized review"
        );
    }
}
