package com.nyle.kra.revenue.commercial;

import java.util.List;

public record DemoUserPersonaResponse(
        String role,
        String user,
        List<String> permissions,
        String demoTask
) {
}
