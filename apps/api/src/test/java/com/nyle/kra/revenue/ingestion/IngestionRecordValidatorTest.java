package com.nyle.kra.revenue.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IngestionRecordValidatorTest {

    private final IngestionRecordValidator validator = new IngestionRecordValidator();

    @Test
    void rejectsMissingRequiredFieldsWithClearReasons() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kra_pin", "P051234567A");
        row.put("taxpayer_type", "COMPANY");
        row.put("status", "ACTIVE");

        RecordValidationResult result = validator.validate(IngestionTable.TAXPAYERS, row, UUID.randomUUID());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues())
                .extracting(IngestionValidationIssue::fieldName)
                .containsExactly("legal_name");
        assertThat(result.issues().getFirst().message()).contains("Required field");
    }

    @Test
    void rejectsInvalidTypedValues() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID().toString());
        row.put("taxpayer_type", "COMPANY");
        row.put("legal_name", "Bad Date Ltd");
        row.put("status", "ACTIVE");
        row.put("registered_at", "not-a-date");

        RecordValidationResult result = validator.validate(IngestionTable.TAXPAYERS, row, UUID.randomUUID());

        assertThat(result.valid()).isFalse();
        assertThat(result.issues())
                .extracting(IngestionValidationIssue::issueType)
                .containsExactly("INVALID_FIELD_VALUE");
    }

    @Test
    void appliesSafeDefaultsForGeneratedAndSourceFields() {
        UUID jobId = UUID.randomUUID();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("invoice_number", "ETIMS-UNIT-1");
        row.put("invoice_date", "2025-01-31");
        row.put("invoice_type", "SALE");
        row.put("invoice_status", "VALID");

        RecordValidationResult result = validator.validate(IngestionTable.INVOICES, row, jobId);

        assertThat(result.valid()).isTrue();
        assertThat(result.values().get("id")).isInstanceOf(UUID.class);
        assertThat(result.values().get("source_job_id")).isEqualTo(jobId);
        assertThat(result.values().get("currency")).isEqualTo("KES");
        assertThat(result.values().get("taxable_amount")).isEqualTo(BigDecimal.ZERO);
        assertThat(result.values().get("created_at")).isInstanceOf(OffsetDateTime.class);
    }
}
