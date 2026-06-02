package com.nyle.kra.revenue.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/governance")
public class AdminGovernanceController {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public AdminGovernanceController(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            AppUserRepository appUserRepository,
            AuditService auditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard")
    public AdminGovernanceDashboardResponse dashboard(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        recordSensitiveRead(authenticatedUser, request, "governance_dashboard");
        return new AdminGovernanceDashboardResponse(
                overview(),
                roles(),
                permissions(),
                rolePermissions(),
                auditLogs(25, false),
                retentionPolicies(),
                privacyChecklist(),
                exportControls(),
                keycloakMfa()
        );
    }

    @GetMapping("/overview")
    public AdminGovernanceOverviewResponse overview() {
        int retentionPolicies = count("SELECT count(*) FROM data_retention_policies WHERE active = TRUE");
        int privacyItems = count("SELECT count(*) FROM privacy_impact_items");
        int completedPrivacyItems = count("SELECT count(*) FROM privacy_impact_items WHERE completed = TRUE");
        return new AdminGovernanceOverviewResponse(
                count("SELECT count(*) FROM app_users"),
                count("SELECT count(*) FROM roles"),
                count("SELECT count(*) FROM permissions"),
                count("SELECT count(*) FROM role_permissions"),
                count("SELECT count(*) FROM data_sources"),
                count("SELECT count(*) FROM risk_rules"),
                count("SELECT count(*) FROM model_versions"),
                count("SELECT count(*) FROM audit_logs"),
                retentionPolicies,
                privacyItems,
                completedPrivacyItems,
                privacyItems > 0 && privacyItems == completedPrivacyItems,
                "PILOT_READY_PATH"
        );
    }

    @GetMapping("/roles")
    public List<AdminRoleResponse> roles() {
        return jdbcTemplate.query("""
                SELECT r.code,
                       r.name,
                       r.description,
                       count(DISTINCT ur.user_id) AS user_count,
                       count(DISTINCT rp.permission_id) AS permission_count
                FROM roles r
                LEFT JOIN user_roles ur ON ur.role_id = r.id
                LEFT JOIN role_permissions rp ON rp.role_id = r.id
                GROUP BY r.id, r.code, r.name, r.description
                ORDER BY r.code
                """, (rs, rowNum) -> new AdminRoleResponse(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("user_count"),
                rs.getInt("permission_count")
        ));
    }

    @GetMapping("/permissions")
    public List<AdminPermissionResponse> permissions() {
        return jdbcTemplate.query("""
                SELECT p.code,
                       p.description,
                       count(DISTINCT rp.role_id) AS role_count
                FROM permissions p
                LEFT JOIN role_permissions rp ON rp.permission_id = p.id
                GROUP BY p.id, p.code, p.description
                ORDER BY p.code
                """, (rs, rowNum) -> new AdminPermissionResponse(
                rs.getString("code"),
                rs.getString("description"),
                rs.getInt("role_count")
        ));
    }

    @GetMapping("/role-permissions")
    public List<RolePermissionResponse> rolePermissions() {
        return jdbcTemplate.query("""
                SELECT r.code,
                       r.name,
                       COALESCE(array_agg(p.code ORDER BY p.code) FILTER (WHERE p.code IS NOT NULL), ARRAY[]::varchar[]) AS permissions
                FROM roles r
                LEFT JOIN role_permissions rp ON rp.role_id = r.id
                LEFT JOIN permissions p ON p.id = rp.permission_id
                GROUP BY r.id, r.code, r.name
                ORDER BY r.code
                """, (rs, rowNum) -> new RolePermissionResponse(
                rs.getString("code"),
                rs.getString("name"),
                List.of((String[]) rs.getArray("permissions").getArray())
        ));
    }

    @GetMapping("/audit-logs")
    public List<AuditLogResponse> auditLogs(
            @RequestParam(defaultValue = "100") int limit,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        recordSensitiveRead(authenticatedUser, request, "audit_log_viewer");
        return auditLogs(limit, true);
    }

    @GetMapping("/retention-policies")
    public List<DataRetentionPolicyResponse> retentionPolicies() {
        return jdbcTemplate.query("""
                SELECT id, data_category, retention_days, policy_reason, active, created_at
                FROM data_retention_policies
                ORDER BY data_category
                """, (rs, rowNum) -> new DataRetentionPolicyResponse(
                rs.getObject("id", UUID.class),
                rs.getString("data_category"),
                rs.getInt("retention_days"),
                rs.getString("policy_reason"),
                rs.getBoolean("active"),
                rs.getObject("created_at", OffsetDateTime.class)
        ));
    }

    @GetMapping("/privacy-impact-checklist")
    public List<PrivacyImpactItemResponse> privacyChecklist() {
        return jdbcTemplate.query("""
                SELECT id,
                       data_category,
                       purpose,
                       lawful_basis,
                       data_minimization_note,
                       masking_required,
                       completed,
                       created_at
                FROM privacy_impact_items
                ORDER BY data_category
                """, (rs, rowNum) -> new PrivacyImpactItemResponse(
                rs.getObject("id", UUID.class),
                rs.getString("data_category"),
                rs.getString("purpose"),
                rs.getString("lawful_basis"),
                rs.getString("data_minimization_note"),
                rs.getBoolean("masking_required"),
                rs.getBoolean("completed"),
                rs.getObject("created_at", OffsetDateTime.class)
        ));
    }

    @GetMapping("/export-controls")
    public ExportControlResponse exportControls() {
        List<String> allowedRoles = jdbcTemplate.queryForList("""
                SELECT r.code
                FROM roles r
                JOIN role_permissions rp ON rp.role_id = r.id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.code = 'EXPORT_SENSITIVE_DATA'
                ORDER BY r.code
                """, String.class);
        return new ExportControlResponse(
                "EXPORT_SENSITIVE_DATA",
                allowedRoles,
                "Bulk exports require an authenticated admin session and an explicit role permission mapping.",
                true
        );
    }

    @GetMapping("/keycloak-mfa")
    public KeycloakMfaPathResponse keycloakMfa() {
        return new KeycloakMfaPathResponse(
                "Keycloak",
                "PILOT_READY_PATH",
                "Use a browser flow with OTP or WebAuthn for pilot/enterprise deployments.",
                List.of("ADMIN", "AUDITOR"),
                true
        );
    }

    private List<AuditLogResponse> auditLogs(int requestedLimit, boolean includeLimitGuard) {
        int limit = Math.max(1, Math.min(requestedLimit, 250));
        if (!includeLimitGuard) {
            limit = Math.min(limit, 25);
        }
        return namedParameterJdbcTemplate.query("""
                SELECT al.id,
                       al.action,
                       au.email AS actor_email,
                       al.entity_type,
                       al.entity_id,
                       al.ip_address,
                       al.user_agent,
                       COALESCE(al.details::text, '{}') AS details,
                       al.created_at
                FROM audit_logs al
                LEFT JOIN app_users au ON au.id = al.actor_user_id
                ORDER BY al.created_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource("limit", limit), this::auditLog);
    }

    private AuditLogResponse auditLog(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLogResponse(
                rs.getObject("id", UUID.class),
                rs.getString("action"),
                rs.getString("actor_email"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                rs.getString("details"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private int count(String sql) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(sql, Integer.class));
    }

    private void recordSensitiveRead(
            AuthenticatedUser authenticatedUser,
            HttpServletRequest request,
            String view
    ) {
        AppUser actor = appUserRepository.findByEmailIgnoreCase(authenticatedUser.getUsername()).orElseThrow();
        auditService.record(
                AuditService.SENSITIVE_ACCESS,
                Optional.of(actor),
                "admin_governance",
                null,
                request,
                Map.<String, Object>of("view", view, "phase", "14")
        );
    }
}

