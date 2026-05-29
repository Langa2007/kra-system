package com.nyle.kra.revenue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
        }
)
class RevenueIntelligenceApiApplicationTests {

    @Test
    void contextLoads() {
    }
}

