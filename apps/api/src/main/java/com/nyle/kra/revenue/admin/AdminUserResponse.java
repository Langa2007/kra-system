package com.nyle.kra.revenue.admin;

import java.util.List;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        String department,
        String status,
        List<String> roles
) {
}
