package com.nyle.kra.revenue.resolution;

import java.util.List;

import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/entity-resolution")
public class EntityResolutionController {

    private final EntityResolutionService entityResolutionService;

    public EntityResolutionController(EntityResolutionService entityResolutionService) {
        this.entityResolutionService = entityResolutionService;
    }

    @PostMapping("/jobs")
    public EntityResolutionSummary run(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        return entityResolutionService.run(authenticatedUser, request);
    }

    @GetMapping("/match-candidates")
    public List<TaxpayerMatchCandidate> matchCandidates(
            @RequestParam String name,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return entityResolutionService.findMatchCandidates(name, limit);
    }

    @GetMapping("/duplicate-candidates")
    public List<DuplicateTaxpayerCandidate> duplicateCandidates() {
        return entityResolutionService.duplicateCandidates();
    }
}
