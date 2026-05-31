package com.nyle.kra.revenue.ingestion;

import java.util.List;
import java.util.Map;

record ParsedUpload(List<Map<String, Object>> records) {
}
