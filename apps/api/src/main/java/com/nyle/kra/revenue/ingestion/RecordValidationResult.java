package com.nyle.kra.revenue.ingestion;

import java.util.List;
import java.util.Map;

record RecordValidationResult(Map<String, Object> values, List<IngestionValidationIssue> issues) {

    boolean valid() {
        return issues.isEmpty();
    }
}
