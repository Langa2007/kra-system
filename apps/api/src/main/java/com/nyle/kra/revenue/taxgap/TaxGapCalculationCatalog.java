package com.nyle.kra.revenue.taxgap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class TaxGapCalculationCatalog {

    private final List<TaxGapRuleConfig> configs = List.of(
            config("VAT_OUTPUT_MISMATCH", "VAT", "0.16", "0.05", "0.01", "undeclared taxable sales"),
            config("VAT_INPUT_MISMATCH", "VAT", "1.00", "0.05", "0.01", "excess input VAT"),
            config("NIL_FILER_ISSUING_INVOICES", "VAT", "0.16", "0.05", "0.01", "nil filer invoice sales"),
            config("IMPORT_TO_SALES_MISMATCH", "INCOME_TAX", "0.30", "0.05", "0.01", "undeclared import-backed income"),
            config("WHT_INCOME_MISMATCH", "WITHHOLDING_TAX", "0.05", "0.05", "0.01", "withholding certificate income"),
            config("PAYE_RATIO_ANOMALY", "PAYE", "1.00", "0.05", "0.01", "expected PAYE shortfall"),
            config("PERMIT_ACTIVE_TAX_INACTIVE", "INCOME_TAX", "0.30", "0.05", "0.01", "active permit exposure"),
            config("PAYMENT_SETTLEMENT_MISMATCH", "REVENUE_ASSURANCE", "1.00", "0.00", "0.00", "unsettled collected revenue"),
            config("RENTAL_INCOME_MISMATCH", "RENTAL_INCOME", "0.10", "0.05", "0.01", "undeclared rental income"),
            config("SECTOR_MARGIN_DEVIATION", "INCOME_TAX", "0.30", "0.05", "0.01", "sector margin shortfall"),
            config("EXPENSE_FROM_NON_COMPLIANT_SUPPLIER", "VAT", "0.16", "0.05", "0.01", "supplier compliance exposure")
    );

    private final Map<String, TaxGapRuleConfig> bySignalType = configs.stream()
            .collect(Collectors.toUnmodifiableMap(TaxGapRuleConfig::signalType, Function.identity()));

    public List<TaxGapRuleConfig> configs() {
        return configs;
    }

    public TaxGapRuleConfig configFor(String signalType) {
        TaxGapRuleConfig config = bySignalType.get(signalType);
        if (config == null) {
            throw new IllegalArgumentException("Unsupported tax gap signal type: " + signalType);
        }
        return config;
    }

    public boolean supports(String signalType) {
        return bySignalType.containsKey(signalType);
    }

    private TaxGapRuleConfig config(
            String signalType,
            String taxHead,
            String recoverableRate,
            String penaltyRate,
            String interestRate,
            String basis
    ) {
        return new TaxGapRuleConfig(
                signalType,
                taxHead,
                new BigDecimal(recoverableRate),
                new BigDecimal(penaltyRate),
                new BigDecimal(interestRate),
                basis
        );
    }
}
