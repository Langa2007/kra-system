package com.nyle.kra.revenue.cases;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaseManagementService {

    private static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public CaseManagementService(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            ObjectMapper objectMapper,
            AppUserRepository appUserRepository,
            AuditService auditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.objectMapper = objectMapper;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
    }

    @Transactional
    public CaseResponse create(CreateCaseRequest request, AuthenticatedUser user, HttpServletRequest httpRequest) {
        if (request.riskSignalId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "riskSignalId is required");
        }
        if (request.assignedTo() != null) {
            requireAdmin(user);
        }
        SignalSnapshot signal = signal(request.riskSignalId());
        BigDecimal recoverable = recoverableAmount(signal);
        String priority = normalizeOrDefault(request.priority(), defaultPriority(recoverable));
        if (!PRIORITIES.contains(priority)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported priority");
        }
        UUID caseId = UUID.randomUUID();
        String title = blankToNull(request.title()) == null
                ? signal.ruleCode() + " - " + signal.taxpayerName()
                : request.title();
        jdbcTemplate.update("""
                INSERT INTO cases (
                    id, case_number, taxpayer_id, risk_signal_id, title, case_type, priority,
                    status, estimated_recoverable_amount, assigned_to
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)
                """,
                caseId,
                caseNumber(),
                signal.taxpayerId(),
                request.riskSignalId(),
                title,
                normalizeOrDefault(request.caseType(), "RISK_SIGNAL"),
                priority,
                recoverable,
                request.assignedTo());
        addEvent(caseId, "OPENED", "Case opened from risk signal " + signal.ruleCode(), user);
        audit(AuditService.CASE_CREATED, user, caseId, httpRequest, Map.of("riskSignalId", request.riskSignalId()));
        return caseById(caseId);
    }

    public List<CaseResponse> list(String status, UUID assignedTo, int limit) {
        return namedJdbcTemplate.query("""
                SELECT c.*, t.kra_pin, t.legal_name, au.full_name AS assigned_name,
                       coalesce(r.assessed_amount, 0) AS assessed_amount,
                       coalesce(r.agreed_amount, 0) AS agreed_amount,
                       coalesce(r.collected_amount, 0) AS collected_amount
                FROM cases c
                LEFT JOIN taxpayers t ON t.id = c.taxpayer_id
                LEFT JOIN app_users au ON au.id = c.assigned_to
                LEFT JOIN LATERAL (
                    SELECT sum(coalesce(assessed_amount, 0)) AS assessed_amount,
                           sum(coalesce(agreed_amount, 0)) AS agreed_amount,
                           sum(coalesce(collected_amount, 0)) AS collected_amount
                    FROM recovery_records rr
                    WHERE rr.case_id = c.id
                ) r ON true
                WHERE (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
                  AND (CAST(:assignedTo AS uuid) IS NULL OR c.assigned_to = CAST(:assignedTo AS uuid))
                ORDER BY c.opened_at DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("status", blankToNull(status))
                        .addValue("assignedTo", assignedTo)
                        .addValue("limit", Math.max(1, Math.min(limit, 200))),
                this::caseResponse);
    }

    public CaseDetailResponse detail(UUID id) {
        return new CaseDetailResponse(caseById(id), events(id), packs(id));
    }

    @Transactional
    public CaseResponse update(UUID id, UpdateCaseRequest request, AuthenticatedUser user, HttpServletRequest httpRequest) {
        CaseResponse existing = caseById(id);
        if (request.assignedTo() != null && !request.assignedTo().equals(existing.assignedTo())) {
            requireAdmin(user);
        }
        String nextStatus = normalizeOrDefault(request.status(), existing.status());
        validateTransition(existing.status(), nextStatus);
        if ("CLOSED".equals(nextStatus) && blankToNull(request.closureReason()) == null && blankToNull(existing.closureReason()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "closureReason is required when closing a case");
        }
        String priority = normalizeOrDefault(request.priority(), existing.priority());
        if (!PRIORITIES.contains(priority)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported priority");
        }
        namedJdbcTemplate.update("""
                UPDATE cases
                SET priority = :priority,
                    status = :status,
                    assigned_to = COALESCE(:assignedTo, assigned_to),
                    closed_at = CASE WHEN :status = 'CLOSED' AND closed_at IS NULL THEN now() ELSE closed_at END,
                    closure_reason = COALESCE(:closureReason, closure_reason)
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("priority", priority)
                        .addValue("status", nextStatus)
                        .addValue("assignedTo", request.assignedTo())
                        .addValue("closureReason", blankToNull(request.closureReason())));
        addEvent(id, "UPDATED", "Case updated to status " + nextStatus, user);
        if (hasRecovery(request)) {
            jdbcTemplate.update("""
                    INSERT INTO recovery_records (
                        case_id, assessed_amount, agreed_amount, collected_amount,
                        collection_date, recovery_status
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    id,
                    request.assessedAmount(),
                    request.agreedAmount(),
                    request.collectedAmount(),
                    request.collectionDate(),
                    normalizeOrDefault(request.recoveryStatus(), "RECORDED"));
            addEvent(id, "RECOVERY_RECORDED", "Recovery amount recorded", user);
        }
        audit(AuditService.CASE_UPDATED, user, id, httpRequest, Map.of("status", nextStatus));
        return caseById(id);
    }

    @Transactional
    public CaseEventResponse addCaseEvent(
            UUID caseId,
            CreateCaseEventRequest request,
            AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        caseById(caseId);
        CaseEventResponse event = addEvent(
                caseId,
                normalizeOrDefault(request.eventType(), "NOTE"),
                blankToNull(request.eventNote()),
                user
        );
        audit(AuditService.CASE_EVENT_ADDED, user, caseId, httpRequest, Map.of("eventType", event.eventType()));
        return event;
    }

    @Transactional
    public EvidencePackResponse generateEvidencePack(UUID caseId, AuthenticatedUser user, HttpServletRequest httpRequest) {
        CaseResponse caseResponse = caseById(caseId);
        SignalSnapshot signal = signal(caseResponse.riskSignalId());
        JsonNode evidence = buildEvidence(caseResponse, signal);
        int version = nextPackVersion(caseId);
        UUID packId = UUID.randomUUID();
        String summary = "Evidence pack for " + caseResponse.caseNumber() + " covering " + signal.ruleCode();
        String fileUri = writePdf(packId, summary, evidence);
        jdbcTemplate.update("""
                INSERT INTO evidence_packs (
                    id, case_id, version, summary, evidence_json, file_uri, generated_by
                )
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """,
                packId,
                caseId,
                version,
                summary,
                evidence.toString(),
                fileUri,
                userId(user));
        addEvent(caseId, "EVIDENCE_PACK_GENERATED", "Evidence pack v" + version + " generated", user);
        audit(AuditService.EVIDENCE_PACK_GENERATED, user, caseId, httpRequest, Map.of("evidencePackId", packId));
        return packById(caseId, packId);
    }

    public EvidencePackResponse evidencePack(UUID caseId, UUID packId) {
        return packById(caseId, packId);
    }

    public byte[] evidencePackPdf(UUID caseId, UUID packId) {
        EvidencePackResponse pack = packById(caseId, packId);
        if (pack.fileUri() == null || !pack.fileUri().startsWith("file:")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF export is not available");
        }
        try {
            return Files.readAllBytes(Path.of(pack.fileUri().substring("file:".length())));
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PDF export is not available", ex);
        }
    }

    private CaseResponse caseById(UUID id) {
        List<CaseResponse> cases = namedJdbcTemplate.query("""
                SELECT c.*, t.kra_pin, t.legal_name, au.full_name AS assigned_name,
                       coalesce(r.assessed_amount, 0) AS assessed_amount,
                       coalesce(r.agreed_amount, 0) AS agreed_amount,
                       coalesce(r.collected_amount, 0) AS collected_amount
                FROM cases c
                LEFT JOIN taxpayers t ON t.id = c.taxpayer_id
                LEFT JOIN app_users au ON au.id = c.assigned_to
                LEFT JOIN LATERAL (
                    SELECT sum(coalesce(assessed_amount, 0)) AS assessed_amount,
                           sum(coalesce(agreed_amount, 0)) AS agreed_amount,
                           sum(coalesce(collected_amount, 0)) AS collected_amount
                    FROM recovery_records rr
                    WHERE rr.case_id = c.id
                ) r ON true
                WHERE c.id = :id
                """,
                new MapSqlParameterSource().addValue("id", id),
                this::caseResponse);
        if (cases.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found");
        }
        return cases.get(0);
    }

    private List<CaseEventResponse> events(UUID caseId) {
        return namedJdbcTemplate.query("""
                SELECT ce.*, au.full_name AS created_by_name
                FROM case_events ce
                LEFT JOIN app_users au ON au.id = ce.created_by
                WHERE ce.case_id = :caseId
                ORDER BY ce.created_at ASC
                """, new MapSqlParameterSource().addValue("caseId", caseId), this::eventResponse);
    }

    private List<EvidencePackResponse> packs(UUID caseId) {
        return namedJdbcTemplate.query("""
                SELECT ep.*, au.full_name AS generated_by_name, ep.evidence_json::text AS evidence_text
                FROM evidence_packs ep
                LEFT JOIN app_users au ON au.id = ep.generated_by
                WHERE ep.case_id = :caseId
                ORDER BY ep.version DESC
                """, new MapSqlParameterSource().addValue("caseId", caseId), this::packResponse);
    }

    private EvidencePackResponse packById(UUID caseId, UUID packId) {
        List<EvidencePackResponse> packs = namedJdbcTemplate.query("""
                SELECT ep.*, au.full_name AS generated_by_name, ep.evidence_json::text AS evidence_text
                FROM evidence_packs ep
                LEFT JOIN app_users au ON au.id = ep.generated_by
                WHERE ep.case_id = :caseId AND ep.id = :packId
                """,
                new MapSqlParameterSource()
                        .addValue("caseId", caseId)
                        .addValue("packId", packId),
                this::packResponse);
        if (packs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence pack not found");
        }
        return packs.get(0);
    }

    private SignalSnapshot signal(UUID riskSignalId) {
        List<SignalSnapshot> signals = namedJdbcTemplate.query("""
                SELECT rs.id, rs.taxpayer_id, t.kra_pin, t.legal_name, rr.code AS rule_code,
                       rs.signal_type, rs.tax_head, rs.period_start, rs.period_end,
                       rs.observed_amount, rs.declared_amount, rs.estimated_gap, rs.confidence_score,
                       rs.severity, rs.explanation, rs.evidence::text AS evidence
                FROM risk_signals rs
                JOIN risk_rules rr ON rr.id = rs.risk_rule_id
                LEFT JOIN taxpayers t ON t.id = rs.taxpayer_id
                WHERE rs.id = :riskSignalId
                """,
                new MapSqlParameterSource().addValue("riskSignalId", riskSignalId),
                (rs, rowNum) -> new SignalSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getObject("taxpayer_id", UUID.class),
                        rs.getString("kra_pin"),
                        rs.getString("legal_name"),
                        rs.getString("rule_code"),
                        rs.getString("signal_type"),
                        rs.getString("tax_head"),
                        rs.getObject("period_start", java.time.LocalDate.class),
                        rs.getObject("period_end", java.time.LocalDate.class),
                        rs.getBigDecimal("observed_amount"),
                        rs.getBigDecimal("declared_amount"),
                        rs.getBigDecimal("estimated_gap"),
                        rs.getBigDecimal("confidence_score"),
                        rs.getString("severity"),
                        rs.getString("explanation"),
                        json(rs.getString("evidence"))
                ));
        if (signals.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk signal not found");
        }
        return signals.get(0);
    }

    private BigDecimal recoverableAmount(SignalSnapshot signal) {
        BigDecimal estimate = namedJdbcTemplate.query("""
                SELECT estimated_recoverable_tax
                FROM tax_gap_estimates
                WHERE taxpayer_id = :taxpayerId
                  AND tax_head = :taxHead
                  AND period_start = :periodStart
                  AND period_end = :periodEnd
                ORDER BY estimated_recoverable_tax DESC
                LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("taxpayerId", signal.taxpayerId())
                        .addValue("taxHead", signal.taxHead())
                        .addValue("periodStart", signal.periodStart())
                        .addValue("periodEnd", signal.periodEnd()),
                rs -> rs.next() ? rs.getBigDecimal("estimated_recoverable_tax") : null);
        return estimate == null ? signal.estimatedGap() : estimate;
    }

    private JsonNode buildEvidence(CaseResponse caseResponse, SignalSnapshot signal) {
        return objectMapper.valueToTree(Map.of(
                "case", Map.of(
                        "id", caseResponse.id(),
                        "caseNumber", caseResponse.caseNumber(),
                        "priority", caseResponse.priority(),
                        "status", caseResponse.status()
                ),
                "taxpayer", Map.of(
                        "id", signal.taxpayerId() == null ? "" : signal.taxpayerId().toString(),
                        "kraPin", nullToEmpty(signal.taxpayerPin()),
                        "name", nullToEmpty(signal.taxpayerName())
                ),
                "period", Map.of(
                        "start", signal.periodStart(),
                        "end", signal.periodEnd()
                ),
                "rule", Map.of(
                        "code", signal.ruleCode(),
                        "signalType", signal.signalType(),
                        "taxHead", nullToEmpty(signal.taxHead())
                ),
                "gap", Map.of(
                        "observedAmount", signal.observedAmount(),
                        "declaredAmount", signal.declaredAmount(),
                        "estimatedGap", signal.estimatedGap(),
                        "estimatedRecoverableAmount", caseResponse.estimatedRecoverableAmount(),
                        "confidenceScore", signal.confidenceScore()
                ),
                "sourceRecords", signal.evidence(),
                "recommendation", "Review source records, contact taxpayer for explanation, and validate recoverable amount before assessment."
        ));
    }

    private String writePdf(UUID packId, String summary, JsonNode evidence) {
        try {
            Path directory = Path.of("target", "evidence-packs").toAbsolutePath();
            Files.createDirectories(directory);
            Path pdf = directory.resolve(packId + ".pdf");
            try (OutputStream output = Files.newOutputStream(pdf)) {
                Document document = new Document();
                PdfWriter.getInstance(document, output);
                document.open();
                document.add(new Paragraph("Revenue Intelligence Evidence Pack"));
                document.add(new Paragraph(summary));
                document.add(new Paragraph("Taxpayer: " + evidence.path("taxpayer").path("name").asText()));
                document.add(new Paragraph("Rule: " + evidence.path("rule").path("code").asText()));
                document.add(new Paragraph("Estimated gap: " + evidence.path("gap").path("estimatedGap").asText()));
                document.add(new Paragraph("Estimated recoverable: " + evidence.path("gap").path("estimatedRecoverableAmount").asText()));
                document.add(new Paragraph("Confidence: " + evidence.path("gap").path("confidenceScore").asText()));
                document.add(new Paragraph("Recommendation: " + evidence.path("recommendation").asText()));
                document.add(new Paragraph("Evidence JSON:"));
                document.add(new Paragraph(evidence.toPrettyString()));
                document.close();
            }
            return "file:" + pdf;
        } catch (IOException | DocumentException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not render evidence pack PDF", ex);
        }
    }

    private CaseEventResponse addEvent(UUID caseId, String eventType, String eventNote, AuthenticatedUser user) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO case_events (id, case_id, event_type, event_note, created_by)
                VALUES (?, ?, ?, ?, ?)
                """, eventId, caseId, eventType, eventNote, userId(user));
        return events(caseId).stream()
                .filter(event -> event.id().equals(eventId))
                .findFirst()
                .orElseThrow();
    }

    private void validateTransition(String current, String next) {
        if (current.equals(next)) {
            return;
        }
        boolean allowed = switch (current) {
            case "OPEN" -> List.of("IN_REVIEW", "CLOSED").contains(next);
            case "IN_REVIEW" -> List.of("AWAITING_TAXPAYER", "CLOSED").contains(next);
            case "AWAITING_TAXPAYER" -> List.of("IN_REVIEW", "CLOSED").contains(next);
            default -> false;
        };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid case status transition");
        }
    }

    private void requireAdmin(AuthenticatedUser user) {
        boolean admin = user != null && user.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can assign officers");
        }
    }

    private void audit(String action, AuthenticatedUser user, UUID caseId, HttpServletRequest request, Map<String, Object> details) {
        auditService.record(action, actor(user), "cases", caseId, request, details);
    }

    private Optional<AppUser> actor(AuthenticatedUser user) {
        if (user == null) {
            return Optional.empty();
        }
        UUID userId = user.getUserId();
        return userId == null ? Optional.empty() : appUserRepository.findById(userId);
    }

    private UUID userId(AuthenticatedUser user) {
        return user == null ? null : user.getUserId();
    }

    private boolean hasRecovery(UpdateCaseRequest request) {
        return request.assessedAmount() != null
                || request.agreedAmount() != null
                || request.collectedAmount() != null
                || request.recoveryStatus() != null;
    }

    private int nextPackVersion(UUID caseId) {
        Integer version = jdbcTemplate.queryForObject(
                "SELECT coalesce(max(version), 0) + 1 FROM evidence_packs WHERE case_id = ?",
                Integer.class,
                caseId
        );
        return version == null ? 1 : version;
    }

    private String caseNumber() {
        return "CASE-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String defaultPriority(BigDecimal recoverable) {
        if (recoverable == null) {
            return "MEDIUM";
        }
        if (recoverable.compareTo(new BigDecimal("1000000")) >= 0) {
            return "CRITICAL";
        }
        if (recoverable.compareTo(new BigDecimal("250000")) >= 0) {
            return "HIGH";
        }
        if (recoverable.compareTo(new BigDecimal("50000")) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid JSON from database", ex);
        }
    }

    private CaseResponse caseResponse(ResultSet rs, int rowNum) throws SQLException {
        return new CaseResponse(
                rs.getObject("id", UUID.class),
                rs.getString("case_number"),
                rs.getObject("risk_signal_id", UUID.class),
                rs.getObject("taxpayer_id", UUID.class),
                rs.getString("kra_pin"),
                rs.getString("legal_name"),
                rs.getString("title"),
                rs.getString("case_type"),
                rs.getString("priority"),
                rs.getString("status"),
                rs.getBigDecimal("estimated_recoverable_amount"),
                rs.getObject("assigned_to", UUID.class),
                rs.getString("assigned_name"),
                instant(rs, "opened_at"),
                instantOrNull(rs, "closed_at"),
                rs.getString("closure_reason"),
                rs.getBigDecimal("assessed_amount"),
                rs.getBigDecimal("agreed_amount"),
                rs.getBigDecimal("collected_amount")
        );
    }

    private CaseEventResponse eventResponse(ResultSet rs, int rowNum) throws SQLException {
        return new CaseEventResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("event_note"),
                rs.getObject("created_by", UUID.class),
                rs.getString("created_by_name"),
                instant(rs, "created_at")
        );
    }

    private EvidencePackResponse packResponse(ResultSet rs, int rowNum) throws SQLException {
        return new EvidencePackResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("case_id", UUID.class),
                rs.getInt("version"),
                rs.getString("summary"),
                json(rs.getString("evidence_text")),
                rs.getString("file_uri"),
                rs.getObject("generated_by", UUID.class),
                rs.getString("generated_by_name"),
                instant(rs, "generated_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record SignalSnapshot(
            UUID id,
            UUID taxpayerId,
            String taxpayerPin,
            String taxpayerName,
            String ruleCode,
            String signalType,
            String taxHead,
            java.time.LocalDate periodStart,
            java.time.LocalDate periodEnd,
            BigDecimal observedAmount,
            BigDecimal declaredAmount,
            BigDecimal estimatedGap,
            BigDecimal confidenceScore,
            String severity,
            String explanation,
            JsonNode evidence
    ) {
    }
}
