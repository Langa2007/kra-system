package com.nyle.kra.revenue.integration;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/integrations")
public class GovernmentIntegrationReadinessController {

    private final GovernmentIntegrationReadinessService service;
    private final AuditService auditService;
    private final AppUserRepository appUserRepository;

    public GovernmentIntegrationReadinessController(
            GovernmentIntegrationReadinessService service,
            AuditService auditService,
            AppUserRepository appUserRepository
    ) {
        this.service = service;
        this.auditService = auditService;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/readiness")
    public GovernmentIntegrationReadinessResponse readiness() {
        return service.dashboard();
    }

    @GetMapping("/adapter-templates")
    public List<AdapterTemplateResponse> adapterTemplates() {
        return service.adapterTemplates();
    }

    @GetMapping("/schema-mappings")
    public List<SourceSchemaMappingResponse> schemaMappings() {
        return service.listMappings();
    }

    @PostMapping("/schema-mappings")
    @ResponseStatus(HttpStatus.CREATED)
    public SourceSchemaMappingResponse createSchemaMapping(
            @Valid @RequestBody CreateSourceSchemaMappingRequest requestBody,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        SourceSchemaMappingResponse response = service.createMapping(requestBody);
        auditService.record(
                AuditService.SOURCE_SCHEMA_MAPPING_CREATED,
                actor(authenticatedUser),
                "source_schema_mappings",
                response.id(),
                request,
                Map.of(
                        "dataSourceId", response.dataSourceId(),
                        "targetEntity", response.targetEntity(),
                        "fieldCount", response.mappingConfig().size()
                )
        );
        return response;
    }

    @GetMapping("/freshness")
    public List<SourceFreshnessResponse> freshness() {
        return service.sourceFreshness();
    }

    @GetMapping("/errors")
    public List<IntegrationErrorResponse> errors() {
        return service.integrationErrors();
    }

    @PostMapping("/mock-adapter-tests")
    public MockAdapterTestResponse testAdapter(
            @Valid @RequestBody MockAdapterTestRequest requestBody,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        MockAdapterTestResponse response = service.testAdapter(requestBody);
        auditService.record(
                AuditService.INTEGRATION_ADAPTER_TESTED,
                actor(authenticatedUser),
                "integration_adapter",
                null,
                request,
                Map.of(
                        "adapterType", response.adapterType(),
                        "status", response.status(),
                        "sanitizedConnectionProfile", response.sanitizedConnectionProfile()
                )
        );
        return response;
    }

    private Optional<AppUser> actor(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            return Optional.empty();
        }
        return appUserRepository.findById(Objects.requireNonNull(authenticatedUser.getUserId()));
    }
}
