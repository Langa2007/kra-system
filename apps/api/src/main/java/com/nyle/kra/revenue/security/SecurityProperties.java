package com.nyle.kra.revenue.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecret,
        long jwtTtlMinutes,
        String defaultAdminEmail,
        String defaultAdminPassword
) {
}
