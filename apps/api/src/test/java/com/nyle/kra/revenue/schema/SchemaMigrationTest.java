package com.nyle.kra.revenue.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.nyle.kra.revenue.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaMigrationTest extends PostgresIntegrationTest {

    private static final List<String> PHASE_2_TABLES = List.of(
            "data_sources",
            "ingestion_jobs",
            "data_quality_issues",
            "taxpayers",
            "taxpayer_identifiers",
            "taxpayer_relationships",
            "tax_obligations",
            "tax_returns",
            "invoices",
            "invoice_lines",
            "customs_declarations",
            "withholding_certificates",
            "payroll_returns",
            "payment_transactions",
            "settlement_records",
            "reconciliation_results",
            "business_permits",
            "properties",
            "risk_rules",
            "risk_signals",
            "risk_scores",
            "tax_gap_estimates",
            "cases",
            "case_events",
            "evidence_packs",
            "notifications",
            "recovery_records",
            "app_users",
            "roles",
            "user_roles",
            "audit_logs",
            "auth_credentials"
    );

    private static final List<String> REQUIRED_INDEXES = List.of(
            "idx_taxpayers_kra_pin",
            "idx_taxpayers_registration_number",
            "idx_taxpayer_identifiers_type_value",
            "idx_tax_returns_taxpayer_period",
            "idx_invoices_supplier_pin_date",
            "idx_taxpayers_legal_name_trgm",
            "idx_taxpayers_trading_name_trgm",
            "idx_ingestion_jobs_data_source_status",
            "idx_data_quality_issues_job",
            "idx_ingestion_jobs_file_hash",
            "idx_invoices_source_job",
            "idx_tax_returns_source_job",
            "idx_customs_importer_pin",
            "idx_properties_owner_pin",
            "idx_payment_transactions_payer_pin",
            "idx_reconciliation_results_date_channel",
            "idx_reconciliation_results_status",
            "idx_reconciliation_results_signal",
            "idx_business_permits_activity_trgm",
            "idx_risk_signals_taxpayer_period",
            "idx_risk_signals_deterministic_key",
            "idx_risk_signals_rule_created",
            "idx_tax_gap_estimates_taxpayer_period",
            "idx_tax_gap_estimates_tax_head_period",
            "idx_tax_gap_estimates_recoverable",
            "idx_risk_scores_taxpayer_period",
            "idx_cases_taxpayer",
            "idx_cases_risk_signal",
            "idx_case_events_case_created",
            "idx_evidence_packs_case_version",
            "idx_recovery_records_case_created",
            "idx_audit_logs_actor_created",
            "idx_audit_logs_action_created"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void cleanDatabaseMigrationCreatesPhaseTwoTablesAndIndexes() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """, String.class);

        assertThat(tables).containsAll(PHASE_2_TABLES);

        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                """, String.class);

        assertThat(indexes).containsAll(REQUIRED_INDEXES);
    }

    @Test
    void seedRolesArePresent() {
        List<String> roles = jdbcTemplate.queryForList("SELECT code FROM roles", String.class);

        assertThat(roles)
                .containsExactlyInAnyOrder("ADMIN", "EXECUTIVE", "OFFICER", "ANALYST", "AUDITOR");
    }
}
