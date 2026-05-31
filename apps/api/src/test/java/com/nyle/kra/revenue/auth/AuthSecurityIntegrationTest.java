package com.nyle.kra.revenue.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyle.kra.revenue.audit.AuditLogRepository;
import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuthSecurityIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void loginIssuesJwtAndWritesAuditLog() throws Exception {
        String token = login();

        assertThat(token).isNotBlank();
        assertThat(auditLogRepository.countByAction(AuditService.LOGIN_SUCCESS)).isEqualTo(1);
    }

    @Test
    void protectedEndpointBlocksUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAllowsJwtAndWritesSensitiveAuditLog() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/admin/security-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.actor").value(TEST_ADMIN_EMAIL));

        assertThat(auditLogRepository.countByAction(AuditService.SENSITIVE_ACCESS)).isEqualTo(1);
    }

    @Test
    void loginRejectsBadPasswordAndWritesAuditLog() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "email": "%s",
                                  "password": "wrong-password"
                                }
                                """.formatted(TEST_ADMIN_EMAIL))))
                .andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.countByAction(AuditService.LOGIN_FAILURE)).isEqualTo(1);
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(TEST_ADMIN_EMAIL, TEST_ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }
}
