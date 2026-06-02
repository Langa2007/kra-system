package com.nyle.kra.revenue.notifications;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final List<NotificationDeliveryAdapter> adapters;
    private final AuditService auditService;
    private final AppUserRepository appUserRepository;

    public NotificationService(
            NamedParameterJdbcTemplate jdbcTemplate,
            List<NotificationDeliveryAdapter> adapters,
            AuditService auditService,
            AppUserRepository appUserRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.adapters = adapters;
        this.auditService = auditService;
        this.appUserRepository = appUserRepository;
    }

    public List<NotificationTemplateResponse> templates() {
        return jdbcTemplate.query("""
                SELECT id, code, channel, subject_template, body_template, active, created_at, updated_at
                FROM notification_templates
                ORDER BY channel, code
                """, new MapSqlParameterSource(), (rs, rowNum) -> template(rs));
    }

    public List<NotificationResponse> history(UUID caseId, UUID riskSignalId, UUID taxpayerId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("caseId", caseId)
                .addValue("riskSignalId", riskSignalId)
                .addValue("taxpayerId", taxpayerId)
                .addValue("limit", Math.max(1, Math.min(limit, 500)));
        return jdbcTemplate.query("""
                SELECT *
                FROM notifications
                WHERE (CAST(:caseId AS uuid) IS NULL OR case_id = :caseId)
                  AND (CAST(:riskSignalId AS uuid) IS NULL OR risk_signal_id = :riskSignalId)
                  AND (CAST(:taxpayerId AS uuid) IS NULL OR taxpayer_id = :taxpayerId)
                ORDER BY created_at DESC
                LIMIT :limit
                """, params, (rs, rowNum) -> notification(rs));
    }

    @Transactional
    public NotificationResponse createNudge(
            CreateNudgeRequest request,
            AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        if (request.caseId() == null && request.riskSignalId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "caseId or riskSignalId is required");
        }

        NudgeSource source = request.caseId() != null
                ? sourceFromCase(request.caseId())
                : sourceFromSignal(Objects.requireNonNull(request.riskSignalId()));
        String channel = normalizeChannel(request.channel());
        TemplateRecord template = loadTemplate(request.templateCode(), channel, source.caseId() != null);
        if (channel == null) {
            channel = template.channel();
        }
        if (!template.channel().equalsIgnoreCase(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Template channel does not match requested channel");
        }

        Map<String, String> values = source.values();
        String subject = render(template.subjectTemplate(), values);
        String body = render(template.bodyTemplate(), values);
        String recipient = request.recipient() == null || request.recipient().isBlank()
                ? defaultRecipient(channel, source)
                : request.recipient().trim();
        UUID notificationId = UUID.randomUUID();
        NotificationDeliveryResult delivery = adapter(channel).send(new NotificationDispatch(
                notificationId,
                channel,
                recipient,
                subject,
                body
        ));

        jdbcTemplate.update("""
                INSERT INTO notifications (
                    id, taxpayer_id, case_id, risk_signal_id, channel, template_code,
                    recipient, subject, message_body, status, sent_at,
                    delivery_provider, delivery_reference
                )
                VALUES (
                    :id, :taxpayerId, :caseId, :riskSignalId, :channel, :templateCode,
                    :recipient, :subject, :messageBody, :status,
                    CASE WHEN :status IN ('SENT', 'DELIVERED') THEN now() ELSE NULL END,
                    :deliveryProvider, :deliveryReference
                )
                """, new MapSqlParameterSource()
                .addValue("id", notificationId)
                .addValue("taxpayerId", source.taxpayerId())
                .addValue("caseId", source.caseId())
                .addValue("riskSignalId", source.riskSignalId())
                .addValue("channel", channel)
                .addValue("templateCode", template.code())
                .addValue("recipient", recipient)
                .addValue("subject", subject)
                .addValue("messageBody", body)
                .addValue("status", delivery.status())
                .addValue("deliveryProvider", delivery.provider())
                .addValue("deliveryReference", delivery.providerReference()));

        auditService.record(
                AuditService.NOTIFICATION_SENT,
                actor(user),
                "notifications",
                notificationId,
                httpRequest,
                Map.<String, Object>of("channel", channel, "templateCode", template.code(), "status", delivery.status())
        );
        return one(notificationId);
    }

    @Transactional
    public NotificationResponse recordResponse(
            UUID notificationId,
            RecordTaxpayerResponseRequest request,
            AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        String responseStatus = request.responseStatus() == null || request.responseStatus().isBlank()
                ? "RECEIVED"
                : request.responseStatus().trim().toUpperCase(Locale.ROOT);
        jdbcTemplate.update("""
                UPDATE notifications
                SET response_status = :responseStatus,
                    response_body = :responseBody,
                    responded_at = now(),
                    status = 'RESPONDED',
                    updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", notificationId)
                .addValue("responseStatus", responseStatus)
                .addValue("responseBody", request.responseBody()));

        auditService.record(
                AuditService.TAXPAYER_RESPONSE_RECORDED,
                actor(user),
                "notifications",
                notificationId,
                httpRequest,
                Map.<String, Object>of("responseStatus", responseStatus)
        );
        return one(notificationId);
    }

    private TemplateRecord loadTemplate(String requestedCode, String channel, boolean fromCase) {
        String code = requestedCode;
        if (code == null || code.isBlank()) {
            if ("SMS".equals(channel)) {
                code = "SOFT_COMPLIANCE_SMS";
            } else if (fromCase) {
                code = "CASE_FOLLOW_UP_EMAIL";
            } else {
                code = "SOFT_COMPLIANCE_EMAIL";
            }
        }
        List<TemplateRecord> templates = jdbcTemplate.query("""
                SELECT code, channel, subject_template, body_template
                FROM notification_templates
                WHERE code = :code
                  AND active = true
                """, new MapSqlParameterSource("code", code), (rs, rowNum) -> new TemplateRecord(
                rs.getString("code"),
                rs.getString("channel"),
                rs.getString("subject_template"),
                rs.getString("body_template")
        ));
        if (templates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification template not found");
        }
        return templates.get(0);
    }

    private NudgeSource sourceFromCase(UUID caseId) {
        List<NudgeSource> sources = jdbcTemplate.query("""
                SELECT c.id AS case_id, c.case_number, c.risk_signal_id,
                       coalesce(c.taxpayer_id, rs.taxpayer_id) AS taxpayer_id,
                       t.kra_pin, t.legal_name,
                       coalesce(rr.code, c.case_type) AS rule_code,
                       coalesce(rs.tax_head, c.case_type) AS tax_head,
                       rs.period_start, rs.period_end,
                       coalesce(rs.estimated_gap, c.estimated_recoverable_amount, 0) AS estimated_gap,
                       coalesce(rs.explanation, c.title) AS explanation
                FROM cases c
                LEFT JOIN risk_signals rs ON rs.id = c.risk_signal_id
                LEFT JOIN risk_rules rr ON rr.id = rs.risk_rule_id
                LEFT JOIN taxpayers t ON t.id = coalesce(c.taxpayer_id, rs.taxpayer_id)
                WHERE c.id = :caseId
                """, new MapSqlParameterSource("caseId", caseId), (rs, rowNum) -> source(rs));
        if (sources.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found");
        }
        return sources.get(0);
    }

    private NudgeSource sourceFromSignal(UUID riskSignalId) {
        List<NudgeSource> sources = jdbcTemplate.query("""
                SELECT NULL::uuid AS case_id, NULL::text AS case_number, rs.id AS risk_signal_id,
                       rs.taxpayer_id, t.kra_pin, t.legal_name,
                       rr.code AS rule_code, rs.tax_head, rs.period_start, rs.period_end,
                       rs.estimated_gap, rs.explanation
                FROM risk_signals rs
                JOIN risk_rules rr ON rr.id = rs.risk_rule_id
                LEFT JOIN taxpayers t ON t.id = rs.taxpayer_id
                WHERE rs.id = :riskSignalId
                """, new MapSqlParameterSource("riskSignalId", riskSignalId), (rs, rowNum) -> source(rs));
        if (sources.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk signal not found");
        }
        return sources.get(0);
    }

    private NudgeSource source(ResultSet rs) throws SQLException {
        return new NudgeSource(
                (UUID) rs.getObject("case_id"),
                rs.getString("case_number"),
                (UUID) rs.getObject("risk_signal_id"),
                (UUID) rs.getObject("taxpayer_id"),
                rs.getString("kra_pin"),
                rs.getString("legal_name"),
                rs.getString("rule_code"),
                rs.getString("tax_head"),
                rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class),
                rs.getBigDecimal("estimated_gap"),
                rs.getString("explanation")
        );
    }

    private NotificationResponse one(UUID id) {
        List<NotificationResponse> responses = jdbcTemplate.query("""
                SELECT *
                FROM notifications
                WHERE id = :id
                """, new MapSqlParameterSource("id", id), (rs, rowNum) -> notification(rs));
        if (responses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return responses.get(0);
    }

    private NotificationDeliveryAdapter adapter(String channel) {
        return adapters.stream()
                .filter(candidate -> candidate.channel().equalsIgnoreCase(channel))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No adapter for channel " + channel));
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        String normalized = channel.trim().toUpperCase(Locale.ROOT);
        if (!List.of("EMAIL", "SMS").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported notification channel");
        }
        return normalized;
    }

    private String defaultRecipient(String channel, NudgeSource source) {
        if ("SMS".equals(channel)) {
            return "+254700000000";
        }
        String pin = source.taxpayerPin() == null || source.taxpayerPin().isBlank()
                ? "taxpayer"
                : source.taxpayerPin().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return pin + "@example.test";
    }

    private String render(String template, Map<String, String> values) {
        if (template == null) {
            return null;
        }
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    private NotificationTemplateResponse template(ResultSet rs) throws SQLException {
        return new NotificationTemplateResponse(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("channel"),
                rs.getString("subject_template"),
                rs.getString("body_template"),
                rs.getBoolean("active"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private NotificationResponse notification(ResultSet rs) throws SQLException {
        return new NotificationResponse(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("taxpayer_id"),
                (UUID) rs.getObject("case_id"),
                (UUID) rs.getObject("risk_signal_id"),
                rs.getString("channel"),
                rs.getString("template_code"),
                rs.getString("recipient"),
                rs.getString("subject"),
                rs.getString("message_body"),
                rs.getString("status"),
                rs.getString("delivery_provider"),
                rs.getString("delivery_reference"),
                rs.getString("response_status"),
                rs.getString("response_body"),
                nullableInstant(rs, "sent_at"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Optional<AppUser> actor(AuthenticatedUser user) {
        if (user == null) {
            return Optional.empty();
        }
        UUID userId = user.getUserId();
        return userId == null ? Optional.empty() : appUserRepository.findById(userId);
    }

    private record TemplateRecord(String code, String channel, String subjectTemplate, String bodyTemplate) {
    }

    private record NudgeSource(
            UUID caseId,
            String caseNumber,
            UUID riskSignalId,
            UUID taxpayerId,
            String taxpayerPin,
            String taxpayerName,
            String ruleCode,
            String taxHead,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal estimatedGap,
            String explanation
    ) {
        Map<String, String> values() {
            Map<String, String> values = new HashMap<>();
            values.put("caseNumber", caseNumber == null ? "unassigned" : caseNumber);
            values.put("taxpayerName", taxpayerName == null ? "taxpayer" : taxpayerName);
            values.put("taxpayerPin", taxpayerPin == null ? "unknown PIN" : taxpayerPin);
            values.put("ruleCode", ruleCode == null ? "compliance review" : ruleCode);
            values.put("taxHead", taxHead == null ? "tax" : taxHead);
            values.put("periodStart", periodStart == null ? "the review period" : periodStart.toString());
            values.put("periodEnd", periodEnd == null ? "the review period" : periodEnd.toString());
            values.put("estimatedGap", estimatedGap == null ? "0.00" : estimatedGap.toPlainString());
            values.put("explanation", explanation == null ? "" : explanation);
            return values;
        }
    }
}
