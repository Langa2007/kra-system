package com.nyle.kra.revenue.ingestion;

import java.util.Comparator;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController {

    private final DataSourceRepository dataSourceRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public DataSourceController(
            DataSourceRepository dataSourceRepository,
            AppUserRepository appUserRepository,
            AuditService auditService
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataSourceResponse create(
            @Valid @RequestBody CreateDataSourceRequest createRequest,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        if (dataSourceRepository.existsByCodeIgnoreCase(createRequest.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Data source code already exists");
        }

        DataSource dataSource = dataSourceRepository.save(new DataSource(
                createRequest.code(),
                createRequest.name(),
                createRequest.sourceType(),
                createRequest.ownerAgency(),
                createRequest.ingestionMethod(),
                createRequest.schemaVersion(),
                createRequest.active() == null || createRequest.active()
        ));

        auditService.record(
                AuditService.DATA_SOURCE_REGISTERED,
                actor(authenticatedUser),
                "data_sources",
                dataSource.getId(),
                request,
                Map.of("code", dataSource.getCode(), "sourceType", dataSource.getSourceType())
        );

        return DataSourceResponse.from(dataSource);
    }

    @GetMapping
    public List<DataSourceResponse> list() {
        return dataSourceRepository.findAll().stream()
                .sorted(Comparator.comparing(DataSource::getCode))
                .map(DataSourceResponse::from)
                .toList();
    }

    private Optional<AppUser> actor(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            return Optional.empty();
        }
        return appUserRepository.findById(Objects.requireNonNull(authenticatedUser.getUserId()));
    }
}
