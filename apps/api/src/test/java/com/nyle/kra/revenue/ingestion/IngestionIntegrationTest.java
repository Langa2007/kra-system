package com.nyle.kra.revenue.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyle.kra.revenue.audit.AuditLogRepository;
import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class IngestionIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void dataSourcesCanBeRegisteredAndListed() throws Exception {
        String token = login();
        String code = "PHASE4_DS_" + suffix();

        mockMvc.perform(post("/api/data-sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "code": "%s",
                                  "name": "Phase 4 Test Source",
                                  "sourceType": "TAXPAYER_REGISTRY",
                                  "ownerAgency": "Project Test",
                                  "ingestionMethod": "BATCH_UPLOAD",
                                  "schemaVersion": "v1"
                                }
                                """.formatted(code))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/data-sources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath(Objects.requireNonNull("$[?(@.code == '%s')]".formatted(code))).exists());

        assertThat(auditLogRepository.countByAction(AuditService.DATA_SOURCE_REGISTERED)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void csvUploadPersistsValidRowsAndQuarantinesInvalidRows() throws Exception {
        String token = login();
        String sourceCode = createSource(token, "CSV");
        String pin = "P" + suffix().substring(0, 9) + "A";
        String csv = """
                id,kra_pin,taxpayer_type,legal_name,trading_name,registration_number,sector_code,sector_name,tax_office,county,status,registered_at,created_at,updated_at
                %s,%s,COMPANY,Valid Phase Four Ltd,Valid Phase Four,BN-90001,S01,Retail,Nairobi,Nairobi,ACTIVE,2024-01-01,2025-04-05T09:00:00Z,2025-04-05T09:00:00Z
                %s,P%sA,COMPANY,,Missing Name,BN-90002,S01,Retail,Nairobi,Nairobi,ACTIVE,2024-01-01,2025-04-05T09:00:00Z,2025-04-05T09:00:00Z
                """.formatted(UUID.randomUUID(), pin, UUID.randomUUID(), suffix().substring(0, 9));

        JsonNode job = upload(token, sourceCode, "taxpayers", "taxpayers.csv", csv, MediaType.TEXT_PLAIN_VALUE);

        assertThat(job.get("status").asText()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(job.get("recordsReceived").asInt()).isEqualTo(2);
        assertThat(job.get("recordsValid").asInt()).isEqualTo(1);
        assertThat(job.get("recordsInvalid").asInt()).isEqualTo(1);
        assertThat(count("taxpayers", "kra_pin", pin)).isEqualTo(1);

        mockMvc.perform(get("/api/ingestion/jobs/{id}/issues", job.get("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].issueType").value("REQUIRED_FIELD_MISSING"))
                .andExpect(jsonPath("$[0].fieldName").value("legal_name"));

        assertThat(auditLogRepository.countByAction(AuditService.INGESTION_JOB_IMPORTED)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void jsonUploadPersistsValidRowsWithSourceJobLink() throws Exception {
        String token = login();
        String sourceCode = createSource(token, "JSON");
        UUID taxpayerId = UUID.randomUUID();
        insertTaxpayer(taxpayerId, "P" + suffix().substring(0, 9) + "B");

        String json = """
                {
                  "records": [
                    {
                      "id": "%s",
                      "taxpayer_id": "%s",
                      "tax_head": "VAT",
                      "period_start": "2025-01-01",
                      "period_end": "2025-01-31",
                      "return_reference": "RET-%s",
                      "declared_sales": "120000.00",
                      "declared_tax_due": "19200.00",
                      "filing_status": "FILED",
                      "filed_at": "2025-02-20T08:30:00Z",
                      "created_at": "2025-04-05T09:00:00Z"
                    }
                  ]
                }
                """.formatted(UUID.randomUUID(), taxpayerId, suffix());

        JsonNode job = upload(token, sourceCode, "tax_returns", "returns.json", json, MediaType.APPLICATION_JSON_VALUE);

        assertThat(job.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(job.get("recordsValid").asInt()).isEqualTo(1);
        Integer linkedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tax_returns WHERE source_job_id = ?",
                Integer.class,
                UUID.fromString(job.get("id").asText())
        );
        assertThat(linkedRows).isEqualTo(1);
    }

    @Test
    void duplicateUploadCreatesDuplicateJobWithoutPersistingRowsAgain() throws Exception {
        String token = login();
        String sourceCode = createSource(token, "DUP");
        String pin = "P" + suffix().substring(0, 9) + "C";
        String csv = taxpayerCsv(UUID.randomUUID(), pin, "Duplicate Phase Four Ltd");

        JsonNode first = upload(token, sourceCode, "taxpayers", "duplicate.csv", csv, MediaType.TEXT_PLAIN_VALUE);
        JsonNode second = upload(token, sourceCode, "taxpayers", "duplicate.csv", csv, MediaType.TEXT_PLAIN_VALUE);

        assertThat(first.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(second.get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(second.get("recordsValid").asInt()).isZero();
        assertThat(count("taxpayers", "kra_pin", pin)).isEqualTo(1);
        assertThat(auditLogRepository.countByAction(AuditService.INGESTION_JOB_DUPLICATE)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void largeCsvUploadIngestsSyntheticTaxpayers() throws Exception {
        String token = login();
        String sourceCode = createSource(token, "LARGE");
        StringBuilder csv = new StringBuilder("""
                id,kra_pin,taxpayer_type,legal_name,trading_name,registration_number,sector_code,sector_name,tax_office,county,status,registered_at,created_at,updated_at
                """);
        for (int i = 0; i < 1_000; i++) {
            csv.append(taxpayerCsvRow(UUID.randomUUID(), "P4L%06dA".formatted(i), "Large Synthetic " + i));
        }

        JsonNode job = upload(token, sourceCode, "taxpayers", "large-taxpayers.csv", csv.toString(), MediaType.TEXT_PLAIN_VALUE);

        assertThat(job.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(job.get("recordsReceived").asInt()).isEqualTo(1_000);
        assertThat(job.get("recordsValid").asInt()).isEqualTo(1_000);
    }

    private JsonNode upload(
            String token,
            String sourceCode,
            String targetTable,
            String fileName,
            String body,
            String contentType
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                body.getBytes(StandardCharsets.UTF_8)
        );

        String response = mockMvc.perform(multipart("/api/ingestion/jobs")
                        .file(file)
                        .param("dataSourceCode", sourceCode)
                        .param("targetTable", targetTable)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private String createSource(String token, String infix) throws Exception {
        String code = "PHASE4_" + infix + "_" + suffix();
        mockMvc.perform(post("/api/data-sources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "code": "%s",
                                  "name": "Phase 4 %s Source",
                                  "sourceType": "SYNTHETIC",
                                  "ownerAgency": "Project Test",
                                  "ingestionMethod": "BATCH_UPLOAD",
                                  "schemaVersion": "v1"
                                }
                                """.formatted(code, infix))))
                .andExpect(status().isCreated());
        return code;
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(TEST_ADMIN_EMAIL, TEST_ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private void insertTaxpayer(UUID taxpayerId, String pin) {
        jdbcTemplate.update("""
                INSERT INTO taxpayers (id, kra_pin, taxpayer_type, legal_name, status)
                VALUES (?, ?, 'COMPANY', 'JSON Phase Four Ltd', 'ACTIVE')
                """, taxpayerId, pin);
    }

    private int count(String table, String field, String value) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + field + " = ?",
                Integer.class,
                value
        );
    }

    private String taxpayerCsv(UUID id, String pin, String legalName) {
        return """
                id,kra_pin,taxpayer_type,legal_name,trading_name,registration_number,sector_code,sector_name,tax_office,county,status,registered_at,created_at,updated_at
                %s""".formatted(taxpayerCsvRow(id, pin, legalName));
    }

    private String taxpayerCsvRow(UUID id, String pin, String legalName) {
        return "%s,%s,COMPANY,%s,%s,BN-%s,S01,Retail,Nairobi,Nairobi,ACTIVE,2024-01-01,2025-04-05T09:00:00Z,2025-04-05T09:00:00Z%n"
                .formatted(id, pin, legalName, legalName.replace("Ltd", "Trading"), suffix());
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
