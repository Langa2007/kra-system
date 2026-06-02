package com.nyle.kra.revenue.admin;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateAdminUserRequest(
        @Email @NotBlank String email,
        @NotBlank String fullName,
        String department,
        String status,
        @NotBlank String password,
        List<String> roles
) {
}
