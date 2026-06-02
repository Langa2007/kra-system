package com.nyle.kra.revenue.admin;

public record AdminRoleResponse(
        String code,
        String name,
        String description,
        int userCount,
        int permissionCount
) {
}

