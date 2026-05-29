package com.nyle.kra.revenue.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void healthReturnsServiceStatus() {
        Map<String, Object> response = new HealthController().health();

        assertThat(response)
                .containsEntry("status", "UP")
                .containsEntry("service", "revenue-intelligence-api")
                .containsKey("timestamp");
    }
}

