package com.nyle.kra.revenue.admin;

import java.util.List;

public record RolePermissionResponse(
        String roleCode,
        String roleName,
        List<String> permissions
) {
}

