package com.nyle.kra.revenue.reports;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final ReportsService reportsService;

    public ReportsController(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @GetMapping("/tax-gap/by-sector")
    public List<TaxGapBySectorResponse> taxGapBySector() {
        return reportsService.taxGapBySector();
    }

    @GetMapping("/tax-gap/by-region")
    public List<TaxGapByRegionResponse> taxGapByRegion() {
        return reportsService.taxGapByRegion();
    }

    @GetMapping("/officer-productivity")
    public List<OfficerProductivityResponse> officerProductivity() {
        return reportsService.officerProductivity();
    }

    @GetMapping("/revenue-recovery")
    public List<RevenueRecoveryResponse> revenueRecovery() {
        return reportsService.revenueRecovery();
    }

    @GetMapping("/audit-pipeline")
    public List<AuditPipelineResponse> auditPipeline() {
        return reportsService.auditPipeline();
    }

    @GetMapping("/exports/tax-gap-by-sector.{format}")
    public ResponseEntity<byte[]> exportTaxGapBySector(@PathVariable String format) {
        ReportExportFormat exportFormat = ReportExportFormat.valueOf(format.toUpperCase(Locale.ROOT));
        byte[] body = reportsService.exportTaxGapBySector(exportFormat);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=%s".formatted(exportFormat.fileName()))
                .contentType(MediaType.parseMediaType(exportFormat.mediaType()))
                .body(body);
    }
}
