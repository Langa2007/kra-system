package com.nyle.kra.revenue.admin;

import java.util.List;

public record KeycloakMfaPathResponse(
        String provider,
        String status,
        String configurationPath,
        List<String> pilotRoles,
        boolean mfaRequiredForPrivilegedRoles
) {
}

