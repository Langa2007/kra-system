package com.nyle.kra.revenue.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DeterministicRuleCatalogTest {

    @Test
    void catalogCoversEveryPhaseSixFirstRule() {
        DeterministicRuleCatalog catalog = new DeterministicRuleCatalog();

        assertThat(catalog.supportedCodes()).containsExactlyInAnyOrderElementsOf(List.of(
                "VAT_OUTPUT_MISMATCH",
                "VAT_INPUT_MISMATCH",
                "IMPORT_TO_SALES_MISMATCH",
                "WHT_INCOME_MISMATCH",
                "NIL_FILER_ISSUING_INVOICES",
                "PAYE_RATIO_ANOMALY",
                "PERMIT_ACTIVE_TAX_INACTIVE",
                "PAYMENT_SETTLEMENT_MISMATCH"
        ));
    }

    @Test
    void everyCatalogRuleIsSupportedAndUnique() {
        DeterministicRuleCatalog catalog = new DeterministicRuleCatalog();

        assertThat(catalog.supportedCodes()).doesNotHaveDuplicates();
        for (String code : catalog.supportedCodes()) {
            assertThat(code).isNotBlank();
            assertThat(catalog.supports(code)).isTrue();
        }
    }
}
