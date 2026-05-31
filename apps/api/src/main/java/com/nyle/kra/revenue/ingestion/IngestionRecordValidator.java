package com.nyle.kra.revenue.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class IngestionRecordValidator {

    private static final Set<String> ZERO_DEFAULT_DECIMAL_COLUMNS = Set.of(
            "taxable_amount",
            "tax_amount",
            "total_amount",
            "customs_value",
            "duty_amount",
            "vat_amount"
    );

    RecordValidationResult validate(IngestionTable table, Map<String, Object> rawRecord, UUID jobId) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<IngestionValidationIssue> issues = new ArrayList<>();

        for (IngestionColumn column : table.columns()) {
            Object rawValue = rawRecord.get(column.name());
            if ("source_job_id".equals(column.name()) && isBlank(rawValue)) {
                values.put(column.name(), jobId);
                continue;
            }
            if ("id".equals(column.name()) && isBlank(rawValue)) {
                values.put(column.name(), UUID.randomUUID());
                continue;
            }
            if (("created_at".equals(column.name()) || "updated_at".equals(column.name())) && isBlank(rawValue)) {
                values.put(column.name(), OffsetDateTime.now());
                continue;
            }
            if ("currency".equals(column.name()) && isBlank(rawValue)) {
                values.put(column.name(), "KES");
                continue;
            }
            if ("confidence_score".equals(column.name()) && isBlank(rawValue)) {
                values.put(column.name(), new BigDecimal("100.00"));
                continue;
            }
            if (ZERO_DEFAULT_DECIMAL_COLUMNS.contains(column.name()) && isBlank(rawValue)) {
                values.put(column.name(), BigDecimal.ZERO);
                continue;
            }

            if (isBlank(rawValue)) {
                if (column.required()) {
                    issues.add(new IngestionValidationIssue("REQUIRED_FIELD_MISSING", column.name(), "Required field is blank"));
                }
                values.put(column.name(), null);
                continue;
            }

            try {
                values.put(column.name(), convert(rawValue, column.type()));
            } catch (RuntimeException ex) {
                issues.add(new IngestionValidationIssue("INVALID_FIELD_VALUE", column.name(), ex.getMessage()));
            }
        }

        return new RecordValidationResult(values, issues);
    }

    private Object convert(Object rawValue, IngestionColumnType type) {
        String value = rawValue.toString().trim();
        return switch (type) {
            case STRING -> value;
            case UUID -> UUID.fromString(value);
            case DATE -> LocalDate.parse(value);
            case OFFSET_DATE_TIME -> OffsetDateTime.parse(value);
            case DECIMAL -> new BigDecimal(value);
            case INTEGER -> Integer.valueOf(value);
        };
    }

    private boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }
}
