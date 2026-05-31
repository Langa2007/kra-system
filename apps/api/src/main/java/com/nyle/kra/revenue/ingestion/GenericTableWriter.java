package com.nyle.kra.revenue.ingestion;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GenericTableWriter {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GenericTableWriter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(IngestionTable table, Map<String, Object> values) {
        String columns = table.columns().stream()
                .map(IngestionColumn::name)
                .collect(Collectors.joining(", "));
        String parameters = table.columns().stream()
                .map(column -> ":" + column.name())
                .collect(Collectors.joining(", "));
        Map<String, ?> parametersMap = Objects.requireNonNull(values);
        jdbcTemplate.update(
                "INSERT INTO " + table.tableName() + " (" + columns + ") VALUES (" + parameters + ")",
                parametersMap
        );
    }
}
