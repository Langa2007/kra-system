package com.nyle.kra.revenue.ingestion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.stereotype.Component;

@Component
public class IngestedFileParser {

    private final ObjectMapper objectMapper;

    public IngestedFileParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ParsedUpload parse(String fileName, byte[] content) {
        String normalized = fileName == null ? "" : fileName.toLowerCase();
        if (normalized.endsWith(".json")) {
            return parseJson(content);
        }
        if (normalized.endsWith(".csv")) {
            return parseCsv(content);
        }
        throw new IllegalArgumentException("Only CSV and JSON uploads are supported");
    }

    private ParsedUpload parseCsv(byte[] content) {
        try (
                InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .build()
                        .parse(reader)
        ) {
            List<Map<String, Object>> records = new ArrayList<>();
            parser.forEach(record -> {
                Map<String, Object> row = new LinkedHashMap<>();
                parser.getHeaderMap().keySet().forEach(header -> row.put(header, record.get(header)));
                records.add(row);
            });
            return new ParsedUpload(records);
        } catch (IOException ex) {
            throw new IllegalArgumentException("CSV file could not be parsed: " + ex.getMessage(), ex);
        }
    }

    private ParsedUpload parseJson(byte[] content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode recordsNode = root.isArray() ? root : root.get("records");
            if (recordsNode == null || !recordsNode.isArray()) {
                throw new IllegalArgumentException("JSON upload must be an array or an object with a records array");
            }

            List<Map<String, Object>> records = new ArrayList<>();
            for (JsonNode recordNode : recordsNode) {
                if (!recordNode.isObject()) {
                    throw new IllegalArgumentException("Each JSON record must be an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> row = objectMapper.convertValue(recordNode, LinkedHashMap.class);
                records.add(row);
            }
            return new ParsedUpload(records);
        } catch (IOException ex) {
            throw new IllegalArgumentException("JSON file could not be parsed: " + ex.getMessage(), ex);
        }
    }
}
