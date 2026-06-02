package com.nyle.kra.revenue.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;
import java.util.UUID;

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
class AdminGovernanceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void governanceDashboardRequiresAdminJwtAndShowsPilotControls() throws Exception {
        long previousSensitiveAccessCount = auditLogRepository.countByAction(AuditService.SENSITIVE_ACCESS);
        String token = login();

        mockMvc.perform(get("/api/admin/governance/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.rolesCount").value(5))
                .andExpect(jsonPath("$.overview.permissionsCount").value(10))
                .andExpect(jsonPath("$.overview.privacyChecklistCompleted").value(true))
                .andExpect(jsonPath("$.overview.keycloakMfaStatus").value("PILOT_READY_PATH"))
                .andExpect(jsonPath("$.exportControls.requiredPermission").value("EXPORT_SENSITIVE_DATA"))
                .andExpect(jsonPath("$.exportControls.bulkExportPermissionControlled").value(true))
                .andExpect(jsonPath("$.keycloakMfa.provider").value("Keycloak"));

        assertThat(auditLogRepository.countByAction(AuditService.SENSITIVE_ACCESS))
                .isEqualTo(previousSensitiveAccessCount + 1);
    }

    @Test
    void governanceDashboardBlocksUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/admin/governance/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolePermissionsCoverPilotPersonasAndSensitiveExportRoles() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/admin/governance/role-permissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.roleCode == 'ADMIN')].permissions").exists())
                .andExpect(jsonPath("$[?(@.roleCode == 'EXECUTIVE')].permissions").exists())
                .andExpect(jsonPath("$[?(@.roleCode == 'OFFICER')].permissions").exists())
                .andExpect(jsonPath("$[?(@.roleCode == 'ANALYST')].permissions").exists())
                .andExpect(jsonPath("$[?(@.roleCode == 'AUDITOR')].permissions").exists());

        mockMvc.perform(get("/api/admin/governance/export-controls")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedRoles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.allowedRoles[1]").value("AUDITOR"));
    }

    @Test
    void auditLogViewerIsReadOnlyThroughAdminApi() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/admin/governance/audit-logs?limit=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").exists());

        mockMvc.perform(post("/api/admin/governance/audit-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void adminCanCreateUpdateAndAssignUserRoles() throws Exception {
        String token = login();
        String email = "phase14-officer-" + UUID.randomUUID() + "@example.test";

        String createResponse = mockMvc.perform(post("/api/admin/governance/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "email": "%s",
                                  "fullName": "Phase Fourteen Officer",
                                  "department": "Compliance",
                                  "status": "ACTIVE",
                                  "password": "temporary-password-123",
                                  "roles": ["OFFICER"]
                                }
                                """.formatted(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("OFFICER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String userId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(patch("/api/admin/governance/users/{id}", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "fullName": "Phase Fourteen Senior Officer",
                                  "department": "Investigations",
                                  "status": "ACTIVE"
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Phase Fourteen Senior Officer"))
                .andExpect(jsonPath("$.department").value("Investigations"));

        mockMvc.perform(put("/api/admin/governance/users/{id}/roles", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "roles": ["ANALYST", "AUDITOR"]
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ANALYST"))
                .andExpect(jsonPath("$.roles[1]").value("AUDITOR"));

        mockMvc.perform(get("/api/admin/governance/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '%s')].roles".formatted(email)).exists());
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
