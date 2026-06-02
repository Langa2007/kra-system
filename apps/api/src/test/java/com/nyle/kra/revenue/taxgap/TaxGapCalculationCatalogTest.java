package com.nyle.kra.revenue.taxgap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaxGapCalculationCatalogTest {

    @Test
    void catalogCoversPhaseSevenTaxGapInputs() {
        TaxGapCalculationCatalog catalog = new TaxGapCalculationCatalog();

        assertThat(catalog.configs())
                .extracting(TaxGapRuleConfig::signalType)
                .containsExactlyInAnyOrder(
                        "VAT_OUTPUT_MISMATCH",
                        "VAT_INPUT_MISMATCH",
                        "NIL_FILER_ISSUING_INVOICES",
                        "IMPORT_TO_SALES_MISMATCH",
                        "WHT_INCOME_MISMATCH",
                        "PAYE_RATIO_ANOMALY",
                        "PERMIT_ACTIVE_TAX_INACTIVE",
                        "PAYMENT_SETTLEMENT_MISMATCH",
                        "RENTAL_INCOME_MISMATCH",
                        "SECTOR_MARGIN_DEVIATION",
                        "EXPENSE_FROM_NON_COMPLIANT_SUPPLIER"
                );
    }

    @Test
    void everyConfigHasRecoverableAndConfidenceBasis() {
        TaxGapCalculationCatalog catalog = new TaxGapCalculationCatalog();

        for (TaxGapRuleConfig config : catalog.configs()) {
            assertThat(config.recoverableRate()).isNotNull();
            assertThat(config.penaltyRate()).isNotNull();
            assertThat(config.interestRate()).isNotNull();
            assertThat(config.basis()).isNotBlank();
            assertThat(catalog.supports(config.signalType())).isTrue();
        }
    }
}
