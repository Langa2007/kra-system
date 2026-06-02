package com.nyle.kra.revenue.ml;

import java.util.List;
import java.util.UUID;

import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ml-risk")
public class RiskScoringController {

    private final RiskScoringService riskScoringService;

    public RiskScoringController(RiskScoringService riskScoringService) {
        this.riskScoringService = riskScoringService;
    }

    @PostMapping("/jobs")
    public RiskScoringJobResponse run(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        return riskScoringService.run(authenticatedUser, request);
    }

    @GetMapping("/predictions")
    public List<ModelPredictionResponse> predictions(
            @RequestParam(required = false) UUID taxpayerId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return riskScoringService.predictions(taxpayerId, limit);
    }

    @GetMapping("/model-versions")
    public List<ModelVersionResponse> modelVersions() {
        return riskScoringService.modelVersions();
    }

    @GetMapping("/dashboard")
    public RiskScoringDashboardResponse dashboard(@RequestParam(defaultValue = "10") int limit) {
        return riskScoringService.dashboard(limit);
    }
}
