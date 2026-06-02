package com.nyle.kra.revenue.cases;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cases")
public class CaseManagementController {

    private final CaseManagementService caseManagementService;

    public CaseManagementController(CaseManagementService caseManagementService) {
        this.caseManagementService = caseManagementService;
    }

    @PostMapping
    public CaseResponse create(
            @RequestBody CreateCaseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        return caseManagementService.create(request, user, httpRequest);
    }

    @GetMapping
    public List<CaseResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return caseManagementService.list(status, assignedTo, limit);
    }

    @GetMapping("/{id}")
    public CaseDetailResponse detail(@PathVariable UUID id) {
        return caseManagementService.detail(id);
    }

    @PatchMapping("/{id}")
    public CaseResponse update(
            @PathVariable UUID id,
            @RequestBody UpdateCaseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        return caseManagementService.update(id, request, user, httpRequest);
    }

    @PostMapping("/{id}/events")
    public CaseEventResponse event(
            @PathVariable UUID id,
            @RequestBody CreateCaseEventRequest request,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        return caseManagementService.addCaseEvent(id, request, user, httpRequest);
    }

    @PostMapping("/{id}/evidence-packs")
    public EvidencePackResponse generateEvidencePack(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest httpRequest
    ) {
        return caseManagementService.generateEvidencePack(id, user, httpRequest);
    }

    @GetMapping("/{id}/evidence-packs/{packId}")
    public ResponseEntity<?> evidencePack(
            @PathVariable UUID id,
            @PathVariable UUID packId,
            @RequestParam(defaultValue = "json") String format
    ) {
        if ("pdf".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_PDF))
                    .body(caseManagementService.evidencePackPdf(id, packId));
        }
        return ResponseEntity.ok(caseManagementService.evidencePack(id, packId));
    }
}
