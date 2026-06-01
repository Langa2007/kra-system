package com.nyle.kra.revenue.reconciliation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nyle.kra.revenue.cases.CaseResponse;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/jobs")
    public ReconciliationRunResponse run(@RequestBody(required = false) ReconciliationRunRequest request) {
        return reconciliationService.run(request == null ? new ReconciliationRunRequest(null, null, null, null) : request);
    }

    @GetMapping("/results")
    public List<ReconciliationResultResponse> results(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return reconciliationService.results(status, from, to, limit);
    }

    @GetMapping("/summary")
    public ReconciliationSummaryResponse summary(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        return reconciliationService.summary(from, to);
    }

    @PostMapping("/results/{id}/case")
    public CaseResponse openCase(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user,
            HttpServletRequest request
    ) {
        return reconciliationService.openCase(id, user, request);
    }
}
