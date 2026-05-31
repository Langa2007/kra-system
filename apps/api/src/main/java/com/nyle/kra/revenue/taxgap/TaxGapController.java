package com.nyle.kra.revenue.taxgap;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tax-gaps")
public class TaxGapController {

    private final TaxGapService taxGapService;

    public TaxGapController(TaxGapService taxGapService) {
        this.taxGapService = taxGapService;
    }

    @PostMapping("/jobs")
    public TaxGapExecutionSummary run(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        return taxGapService.run(authenticatedUser, request);
    }

    @GetMapping("/estimates")
    public List<TaxGapEstimateResponse> estimates(
            @RequestParam(required = false) String taxHead,
            @RequestParam(required = false) UUID taxpayerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return taxGapService.estimates(taxHead, taxpayerId, periodStart, periodEnd, limit);
    }

    @GetMapping("/summary")
    public List<TaxGapSummaryResponse> summary() {
        return taxGapService.summary();
    }

    @GetMapping("/ranking")
    public List<TaxpayerGapRankingResponse> ranking(@RequestParam(defaultValue = "50") int limit) {
        return taxGapService.ranking(limit);
    }
}
