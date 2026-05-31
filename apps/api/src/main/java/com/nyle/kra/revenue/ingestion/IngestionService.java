package com.nyle.kra.revenue.ingestion;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IngestionService {

    private static final List<String> DUPLICATE_SOURCE_STATUSES = List.of("COMPLETED", "COMPLETED_WITH_ERRORS");

    private final DataSourceRepository dataSourceRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final DataQualityIssueRepository dataQualityIssueRepository;
    private final AppUserRepository appUserRepository;
    private final IngestedFileParser fileParser;
    private final IngestionRecordValidator validator;
    private final GenericTableWriter tableWriter;
    private final AuditService auditService;

    public IngestionService(
            DataSourceRepository dataSourceRepository,
            IngestionJobRepository ingestionJobRepository,
            DataQualityIssueRepository dataQualityIssueRepository,
            AppUserRepository appUserRepository,
            IngestedFileParser fileParser,
            IngestionRecordValidator validator,
            GenericTableWriter tableWriter,
            AuditService auditService
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.dataQualityIssueRepository = dataQualityIssueRepository;
        this.appUserRepository = appUserRepository;
        this.fileParser = fileParser;
        this.validator = validator;
        this.tableWriter = tableWriter;
        this.auditService = auditService;
    }

    public IngestionJob ingest(
            String dataSourceCode,
            String targetTableName,
            MultipartFile file,
            AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        DataSource dataSource = dataSourceRepository.findByCodeIgnoreCase(dataSourceCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Data source not found"));
        IngestionTable table = IngestionTable.from(targetTableName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported target table"));

        byte[] content = read(file);
        String fileHash = sha256(content);
        UUID actorId = authenticatedUser == null ? null : authenticatedUser.getUserId();

        Optional<IngestionJob> duplicate = ingestionJobRepository
                .findFirstByDataSourceIdAndTargetTableAndFileSha256AndStatusInOrderByStartedAtAsc(
                        dataSource.getId(),
                        table.tableName(),
                        fileHash,
                        DUPLICATE_SOURCE_STATUSES
                );

        IngestionJob job = new IngestionJob(dataSource, file.getOriginalFilename(), table.tableName(), fileHash, actorId);
        ingestionJobRepository.save(job);

        if (duplicate.isPresent()) {
            job.duplicateOf(duplicate.get().getId());
            ingestionJobRepository.save(job);
            auditService.record(
                    AuditService.INGESTION_JOB_DUPLICATE,
                    actor(actorId),
                    "ingestion_jobs",
                    job.getId(),
                    request,
                    Map.of("originalJobId", duplicate.get().getId().toString(), "targetTable", table.tableName())
            );
            return job;
        }

        ParsedUpload parsedUpload;
        try {
            parsedUpload = fileParser.parse(file.getOriginalFilename(), content);
        } catch (IllegalArgumentException ex) {
            job.fail(ex.getMessage());
            ingestionJobRepository.save(job);
            recordIssue(job, dataSource, "ERROR", "FILE_PARSE_ERROR", "file", null, ex.getMessage(), Map.of());
            return job;
        }

        long valid = 0;
        long invalid = 0;
        int rowNumber = 1;
        for (Map<String, Object> rawRecord : parsedUpload.records()) {
            RecordValidationResult result = validator.validate(table, rawRecord, job.getId());
            if (!result.valid()) {
                invalid++;
                recordValidationIssues(job, dataSource, rawRecord, rowNumber, result.issues());
                rowNumber++;
                continue;
            }

            try {
                tableWriter.insert(table, result.values());
                valid++;
            } catch (DataAccessException ex) {
                invalid++;
                recordIssue(
                        job,
                        dataSource,
                        "ERROR",
                        "PERSISTENCE_ERROR",
                        recordReference(rawRecord, rowNumber),
                        null,
                        concise(ex),
                        objectPayload(rawRecord)
                );
            }
            rowNumber++;
        }

        String summary = invalid == 0 ? null : invalid + " invalid row(s) quarantined in data_quality_issues";
        job.complete(parsedUpload.records().size(), valid, invalid, summary);
        ingestionJobRepository.save(job);
        auditService.record(
                AuditService.INGESTION_JOB_IMPORTED,
                actor(actorId),
                "ingestion_jobs",
                job.getId(),
                request,
                Map.of(
                        "targetTable", table.tableName(),
                        "recordsReceived", parsedUpload.records().size(),
                        "recordsValid", valid,
                        "recordsInvalid", invalid
                )
        );
        return job;
    }

    private void recordValidationIssues(
            IngestionJob job,
            DataSource dataSource,
            Map<String, Object> rawRecord,
            int rowNumber,
            List<IngestionValidationIssue> issues
    ) {
        for (IngestionValidationIssue issue : issues) {
            recordIssue(
                    job,
                    dataSource,
                    "ERROR",
                    issue.issueType(),
                    recordReference(rawRecord, rowNumber),
                    issue.fieldName(),
                    issue.message(),
                    objectPayload(rawRecord)
            );
        }
    }

    private void recordIssue(
            IngestionJob job,
            DataSource dataSource,
            String severity,
            String issueType,
            String recordReference,
            String fieldName,
            String issueMessage,
            Map<String, Object> recordPayload
    ) {
        dataQualityIssueRepository.save(new DataQualityIssue(
                job,
                dataSource,
                severity,
                issueType,
                recordReference,
                fieldName,
                issueMessage,
                recordPayload
        ));
    }

    private Optional<AppUser> actor(UUID actorId) {
        if (actorId == null) {
            return Optional.empty();
        }
        return appUserRepository.findById(actorId);
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload file is required");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload file could not be read", ex);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private Map<String, Object> objectPayload(Map<String, Object> rawRecord) {
        return new LinkedHashMap<>(rawRecord);
    }

    private String recordReference(Map<String, Object> rawRecord, int rowNumber) {
        for (String field : List.of(
                "id",
                "invoice_number",
                "return_reference",
                "declaration_number",
                "certificate_number",
                "permit_number",
                "property_reference",
                "transaction_reference",
                "settlement_reference",
                "kra_pin"
        )) {
            Object value = rawRecord.get(field);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return "row:" + rowNumber;
    }

    private String concise(DataAccessException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        if (message == null || message.isBlank()) {
            return ex.getMessage();
        }
        int detailIndex = message.indexOf("Detail:");
        return detailIndex >= 0 ? message.substring(0, detailIndex).trim() : message;
    }
}
