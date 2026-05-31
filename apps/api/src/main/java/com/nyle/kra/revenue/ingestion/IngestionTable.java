package com.nyle.kra.revenue.ingestion;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

enum IngestionTable {
    TAXPAYERS("taxpayers", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("kra_pin", IngestionColumnType.STRING, false),
            col("taxpayer_type", IngestionColumnType.STRING, true),
            col("legal_name", IngestionColumnType.STRING, true),
            col("trading_name", IngestionColumnType.STRING, false),
            col("registration_number", IngestionColumnType.STRING, false),
            col("sector_code", IngestionColumnType.STRING, false),
            col("sector_name", IngestionColumnType.STRING, false),
            col("tax_office", IngestionColumnType.STRING, false),
            col("county", IngestionColumnType.STRING, false),
            col("status", IngestionColumnType.STRING, true),
            col("registered_at", IngestionColumnType.DATE, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false),
            col("updated_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    TAXPAYER_IDENTIFIERS("taxpayer_identifiers", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("taxpayer_id", IngestionColumnType.UUID, true),
            col("identifier_type", IngestionColumnType.STRING, true),
            col("identifier_value", IngestionColumnType.STRING, true),
            col("source", IngestionColumnType.STRING, false),
            col("confidence_score", IngestionColumnType.DECIMAL, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    TAX_OBLIGATIONS("tax_obligations", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("taxpayer_id", IngestionColumnType.UUID, true),
            col("tax_head", IngestionColumnType.STRING, true),
            col("obligation_status", IngestionColumnType.STRING, true),
            col("effective_from", IngestionColumnType.DATE, false),
            col("effective_to", IngestionColumnType.DATE, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    INVOICES("invoices", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("invoice_number", IngestionColumnType.STRING, true),
            col("supplier_taxpayer_id", IngestionColumnType.UUID, false),
            col("buyer_taxpayer_id", IngestionColumnType.UUID, false),
            col("supplier_pin", IngestionColumnType.STRING, false),
            col("buyer_pin", IngestionColumnType.STRING, false),
            col("invoice_date", IngestionColumnType.DATE, true),
            col("invoice_type", IngestionColumnType.STRING, true),
            col("invoice_status", IngestionColumnType.STRING, true),
            col("taxable_amount", IngestionColumnType.DECIMAL, false),
            col("tax_amount", IngestionColumnType.DECIMAL, false),
            col("total_amount", IngestionColumnType.DECIMAL, false),
            col("currency", IngestionColumnType.STRING, false),
            col("etims_reference", IngestionColumnType.STRING, false),
            col("source_job_id", IngestionColumnType.UUID, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    INVOICE_LINES("invoice_lines", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("invoice_id", IngestionColumnType.UUID, true),
            col("line_number", IngestionColumnType.INTEGER, true),
            col("item_description", IngestionColumnType.STRING, false),
            col("quantity", IngestionColumnType.DECIMAL, false),
            col("unit_price", IngestionColumnType.DECIMAL, false),
            col("taxable_amount", IngestionColumnType.DECIMAL, false),
            col("tax_amount", IngestionColumnType.DECIMAL, false),
            col("hs_code", IngestionColumnType.STRING, false),
            col("product_code", IngestionColumnType.STRING, false)
    )),
    TAX_RETURNS("tax_returns", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("taxpayer_id", IngestionColumnType.UUID, true),
            col("tax_head", IngestionColumnType.STRING, true),
            col("period_start", IngestionColumnType.DATE, true),
            col("period_end", IngestionColumnType.DATE, true),
            col("return_reference", IngestionColumnType.STRING, false),
            col("declared_sales", IngestionColumnType.DECIMAL, false),
            col("declared_income", IngestionColumnType.DECIMAL, false),
            col("declared_tax_due", IngestionColumnType.DECIMAL, false),
            col("declared_input_tax", IngestionColumnType.DECIMAL, false),
            col("declared_output_tax", IngestionColumnType.DECIMAL, false),
            col("filing_status", IngestionColumnType.STRING, true),
            col("filed_at", IngestionColumnType.OFFSET_DATE_TIME, false),
            col("source_job_id", IngestionColumnType.UUID, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    CUSTOMS_DECLARATIONS("customs_declarations", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("taxpayer_id", IngestionColumnType.UUID, false),
            col("importer_pin", IngestionColumnType.STRING, false),
            col("declaration_number", IngestionColumnType.STRING, true),
            col("declaration_type", IngestionColumnType.STRING, true),
            col("declaration_date", IngestionColumnType.DATE, true),
            col("hs_code", IngestionColumnType.STRING, false),
            col("goods_description", IngestionColumnType.STRING, false),
            col("country_of_origin", IngestionColumnType.STRING, false),
            col("customs_value", IngestionColumnType.DECIMAL, false),
            col("duty_amount", IngestionColumnType.DECIMAL, false),
            col("vat_amount", IngestionColumnType.DECIMAL, false),
            col("total_landed_cost", IngestionColumnType.DECIMAL, false),
            col("source_job_id", IngestionColumnType.UUID, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    WITHHOLDING_CERTIFICATES("withholding_certificates", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("certificate_number", IngestionColumnType.STRING, true),
            col("payer_taxpayer_id", IngestionColumnType.UUID, false),
            col("payee_taxpayer_id", IngestionColumnType.UUID, false),
            col("payer_pin", IngestionColumnType.STRING, false),
            col("payee_pin", IngestionColumnType.STRING, false),
            col("certificate_date", IngestionColumnType.DATE, true),
            col("payment_period_start", IngestionColumnType.DATE, false),
            col("payment_period_end", IngestionColumnType.DATE, false),
            col("gross_amount", IngestionColumnType.DECIMAL, true),
            col("withheld_amount", IngestionColumnType.DECIMAL, true),
            col("tax_rate", IngestionColumnType.DECIMAL, false),
            col("source_job_id", IngestionColumnType.UUID, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    PAYROLL_RETURNS("payroll_returns", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("taxpayer_id", IngestionColumnType.UUID, true),
            col("period_start", IngestionColumnType.DATE, true),
            col("period_end", IngestionColumnType.DATE, true),
            col("employee_count", IngestionColumnType.INTEGER, false),
            col("gross_pay", IngestionColumnType.DECIMAL, false),
            col("paye_due", IngestionColumnType.DECIMAL, false),
            col("paye_paid", IngestionColumnType.DECIMAL, false),
            col("filing_status", IngestionColumnType.STRING, true),
            col("source_job_id", IngestionColumnType.UUID, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    BUSINESS_PERMITS("business_permits", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("taxpayer_id", IngestionColumnType.UUID, false),
            col("permit_number", IngestionColumnType.STRING, true),
            col("county", IngestionColumnType.STRING, true),
            col("business_activity", IngestionColumnType.STRING, false),
            col("premises_location", IngestionColumnType.STRING, false),
            col("valid_from", IngestionColumnType.DATE, false),
            col("valid_to", IngestionColumnType.DATE, false),
            col("permit_fee", IngestionColumnType.DECIMAL, false),
            col("permit_status", IngestionColumnType.STRING, true),
            col("source_job_id", IngestionColumnType.UUID, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    PROPERTIES("properties", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("property_reference", IngestionColumnType.STRING, true),
            col("owner_taxpayer_id", IngestionColumnType.UUID, false),
            col("owner_pin", IngestionColumnType.STRING, false),
            col("county", IngestionColumnType.STRING, false),
            col("location_description", IngestionColumnType.STRING, false),
            col("property_type", IngestionColumnType.STRING, false),
            col("valuation_amount", IngestionColumnType.DECIMAL, false),
            col("estimated_monthly_rent", IngestionColumnType.DECIMAL, false),
            col("source_job_id", IngestionColumnType.UUID, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    PAYMENT_TRANSACTIONS("payment_transactions", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("transaction_reference", IngestionColumnType.STRING, true),
            col("payer_taxpayer_id", IngestionColumnType.UUID, false),
            col("payer_pin", IngestionColumnType.STRING, false),
            col("collecting_agency", IngestionColumnType.STRING, true),
            col("revenue_channel", IngestionColumnType.STRING, true),
            col("service_code", IngestionColumnType.STRING, false),
            col("payment_date", IngestionColumnType.OFFSET_DATE_TIME, true),
            col("amount", IngestionColumnType.DECIMAL, true),
            col("currency", IngestionColumnType.STRING, false),
            col("payment_status", IngestionColumnType.STRING, true),
            col("provider_reference", IngestionColumnType.STRING, false),
            col("source_job_id", IngestionColumnType.UUID, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    )),
    SETTLEMENT_RECORDS("settlement_records", List.of(
            col("id", IngestionColumnType.UUID, false),
            col("settlement_reference", IngestionColumnType.STRING, true),
            col("collecting_agency", IngestionColumnType.STRING, true),
            col("revenue_channel", IngestionColumnType.STRING, true),
            col("settlement_account", IngestionColumnType.STRING, false),
            col("settlement_date", IngestionColumnType.DATE, true),
            col("settled_amount", IngestionColumnType.DECIMAL, true),
            col("transaction_count", IngestionColumnType.INTEGER, false),
            col("settlement_status", IngestionColumnType.STRING, true),
            col("source_job_id", IngestionColumnType.UUID, false),
            col("created_at", IngestionColumnType.OFFSET_DATE_TIME, false)
    ));

    private static final Map<String, IngestionTable> BY_TABLE_NAME = List.of(values()).stream()
            .collect(Collectors.toMap(table -> table.tableName, Function.identity()));

    private final String tableName;
    private final List<IngestionColumn> columns;
    private final Map<String, IngestionColumn> columnsByName;

    IngestionTable(String tableName, List<IngestionColumn> columns) {
        this.tableName = tableName;
        this.columns = columns;
        this.columnsByName = columns.stream().collect(Collectors.toMap(IngestionColumn::name, Function.identity()));
    }

    static Optional<IngestionTable> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_TABLE_NAME.get(value.toLowerCase(Locale.ROOT)));
    }

    private static IngestionColumn col(String name, IngestionColumnType type, boolean required) {
        return new IngestionColumn(name, type, required);
    }

    String tableName() {
        return tableName;
    }

    List<IngestionColumn> columns() {
        return columns;
    }

    Optional<IngestionColumn> column(String name) {
        return Optional.ofNullable(columnsByName.get(name));
    }

    boolean hasColumn(String name) {
        return columnsByName.containsKey(name);
    }

    boolean hasSourceJob() {
        return hasColumn("source_job_id");
    }
}
