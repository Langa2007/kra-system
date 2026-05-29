package com.nyle.kra.revenue.health;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "revenue-intelligence-api",
                "timestamp", Instant.now().toString()
        );
    }
}

