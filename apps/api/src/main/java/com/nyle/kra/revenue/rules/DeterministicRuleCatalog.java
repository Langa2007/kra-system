package com.nyle.kra.revenue.rules;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class DeterministicRuleCatalog {

    private final List<String> supportedCodes = List.of(
            "VAT_OUTPUT_MISMATCH",
            "VAT_INPUT_MISMATCH",
            "IMPORT_TO_SALES_MISMATCH",
            "WHT_INCOME_MISMATCH",
            "NIL_FILER_ISSUING_INVOICES",
            "PAYE_RATIO_ANOMALY",
            "PERMIT_ACTIVE_TAX_INACTIVE",
            "PAYMENT_SETTLEMENT_MISMATCH"
    );

    public List<String> supportedCodes() {
        return supportedCodes;
    }

    public boolean supports(String code) {
        return supportedCodes.contains(code);
    }
}
