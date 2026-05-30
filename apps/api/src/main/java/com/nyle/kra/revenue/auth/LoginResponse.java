package com.nyle.kra.revenue.auth;

import java.util.List;

public record LoginResponse(
        String tokenType,
        String accessToken,
        UserSummary user
) {

    public record UserSummary(
            String id,
            String email,
            String fullName,
            List<String> roles
    ) {
    }
}
