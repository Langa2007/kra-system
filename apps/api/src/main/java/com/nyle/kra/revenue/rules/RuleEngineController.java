package com.nyle.kra.revenue.rules;

import java.util.List;
import java.util.UUID;

import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
public class RuleEngineController {

    private final RuleEngineService ruleEngineService;

    public RuleEngineController(RuleEngineService ruleEngineService) {
        this.ruleEngineService = ruleEngineService;
    }

    @GetMapping
    public List<RuleDefinitionResponse> rules() {
        return ruleEngineService.listRules();
    }

    @PutMapping("/{code}/thresholds")
    public RuleDefinitionResponse updateRule(
            @PathVariable String code,
            @RequestBody RuleThresholdUpdateRequest request
    ) {
        return ruleEngineService.updateRule(code, request);
    }

    @PostMapping("/jobs")
    public RuleExecutionSummary run(
            @RequestParam(name = "code", required = false) List<String> codes,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        return ruleEngineService.runRules(codes, authenticatedUser, request);
    }

    @GetMapping("/signals")
    public List<RiskSignalResponse> signals(
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) UUID taxpayerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ruleEngineService.listSignals(ruleCode, taxpayerId, status, limit);
    }
}
