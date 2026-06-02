package com.nyle.kra.revenue.admin;

public record AdminGovernanceOverviewResponse(
        int usersCount,
        int rolesCount,
        int permissionsCount,
        int rolePermissionMappingsCount,
        int dataSourcesCount,
        int riskRulesCount,
        int modelVersionsCount,
        int auditLogsCount,
        int activeRetentionPoliciesCount,
        int privacyChecklistCount,
        int privacyChecklistCompletedCount,
        boolean privacyChecklistCompleted,
        String keycloakMfaStatus
) {
}

