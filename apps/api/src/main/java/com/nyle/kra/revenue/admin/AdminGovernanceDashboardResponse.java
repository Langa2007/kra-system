package com.nyle.kra.revenue.admin;

import java.util.List;

public record AdminGovernanceDashboardResponse(
        AdminGovernanceOverviewResponse overview,
        List<AdminRoleResponse> roles,
        List<AdminPermissionResponse> permissions,
        List<RolePermissionResponse> rolePermissions,
        List<AuditLogResponse> auditLogs,
        List<DataRetentionPolicyResponse> retentionPolicies,
        List<PrivacyImpactItemResponse> privacyImpactChecklist,
        ExportControlResponse exportControls,
        KeycloakMfaPathResponse keycloakMfa
) {
}

