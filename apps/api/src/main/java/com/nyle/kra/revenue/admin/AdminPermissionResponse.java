package com.nyle.kra.revenue.admin;

public record AdminPermissionResponse(
        String code,
        String description,
        int roleCount
) {
}

