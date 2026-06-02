package com.nyle.kra.revenue.admin;

public record UpdateAdminUserRequest(
        String fullName,
        String department,
        String status
) {
}
