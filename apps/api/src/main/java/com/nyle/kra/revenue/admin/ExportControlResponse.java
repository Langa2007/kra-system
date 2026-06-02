package com.nyle.kra.revenue.admin;

import java.util.List;

public record ExportControlResponse(
        String requiredPermission,
        List<String> allowedRoles,
        String policy,
        boolean bulkExportPermissionControlled
) {
}

