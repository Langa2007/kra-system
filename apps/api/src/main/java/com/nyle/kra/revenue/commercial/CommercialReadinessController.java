package com.nyle.kra.revenue.commercial;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commercial")
public class CommercialReadinessController {

    private final CommercialReadinessService commercialReadinessService;

    public CommercialReadinessController(CommercialReadinessService commercialReadinessService) {
        this.commercialReadinessService = commercialReadinessService;
    }

    @GetMapping("/pilot-package")
    public PilotPackageResponse pilotPackage() {
        return commercialReadinessService.pilotPackage();
    }

    @GetMapping("/roi")
    public RoiSummaryResponse roi() {
        return commercialReadinessService.roiSummary();
    }

    @GetMapping("/exports/pilot-package.pdf")
    public ResponseEntity<byte[]> pilotPackagePdf() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=phase16-pilot-package.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(commercialReadinessService.pilotPackagePdf());
    }

    @GetMapping("/exports/roi-calculator.xlsx")
    public ResponseEntity<byte[]> roiWorkbook() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=phase16-roi-calculator.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(commercialReadinessService.roiWorkbook());
    }
}
