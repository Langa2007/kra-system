package com.nyle.kra.revenue.commercial;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CommercialReadinessService {

    private static final BigDecimal DEFAULT_PILOT_COST = new BigDecimal("18500000");
    private static final BigDecimal DEFAULT_COLLECTION_RATE = new BigDecimal("0.18");

    private final JdbcTemplate jdbcTemplate;

    public CommercialReadinessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PilotPackageResponse pilotPackage() {
        return new PilotPackageResponse(
                "Phase 16",
                "Controlled pilot and buyer conversation ready",
                "Demonstrate explainable revenue recovery, settlement assurance, and officer workflow using synthetic or approved pilot data only.",
                roiSummary(),
                documents(),
                demoUsers(),
                sampleDashboards(),
                procurementRoutes(),
                "Synthetic data is the default demo source. Approved pilot data can be loaded by source system, mapped, validated, and audited before any dashboard or evidence pack is shown.",
                "Local Docker Compose for demos, then Spring Boot, PostgreSQL, Next.js, MinIO, Neo4j, and Keycloak-compatible identity for controlled pilot environments.",
                "Annual license plus implementation fee, with optional success-based components only where public procurement rules permit."
        );
    }

    public RoiSummaryResponse roiSummary() {
        BigDecimal estimatedGap = singleMoney("""
                SELECT COALESCE(sum(estimated_gap), 0)
                FROM tax_gap_estimates
                """);
        BigDecimal recoverableTax = singleMoney("""
                SELECT COALESCE(sum(estimated_recoverable_tax), 0)
                FROM tax_gap_estimates
                """);
        BigDecimal collectedAmount = singleMoney("""
                SELECT COALESCE(sum(collected_amount), 0)
                FROM recovery_records
                """);
        BigDecimal settlementVariance = singleMoney("""
                SELECT COALESCE(sum(variance_amount), 0)
                FROM reconciliation_results
                WHERE settlement_status <> 'MATCHED'
                """);
        long openCases = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM cases
                WHERE status <> 'CLOSED'
                """, Long.class);
        BigDecimal expectedRecoveredRevenue = recoverableTax.multiply(DEFAULT_COLLECTION_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netBenefit = expectedRecoveredRevenue.subtract(DEFAULT_PILOT_COST).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roiMultiple = DEFAULT_PILOT_COST.signum() == 0
                ? BigDecimal.ZERO
                : expectedRecoveredRevenue.divide(DEFAULT_PILOT_COST, 4, RoundingMode.HALF_UP);
        BigDecimal paybackMonths = expectedRecoveredRevenue.signum() == 0
                ? BigDecimal.ZERO
                : DEFAULT_PILOT_COST.divide(expectedRecoveredRevenue.divide(new BigDecimal("12"), 8, RoundingMode.HALF_UP), 2, RoundingMode.HALF_UP);

        return new RoiSummaryResponse(
                estimatedGap,
                recoverableTax,
                collectedAmount,
                settlementVariance,
                openCases,
                DEFAULT_PILOT_COST,
                DEFAULT_COLLECTION_RATE,
                expectedRecoveredRevenue,
                netBenefit,
                roiMultiple,
                paybackMonths
        );
    }

    public byte[] pilotPackagePdf() {
        PilotPackageResponse packageResponse = pilotPackage();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("KRA Revenue Intelligence Pilot Package"));
            document.add(new Paragraph(packageResponse.buyerReadiness()));
            document.add(new Paragraph("Objective: " + packageResponse.pilotObjective()));
            document.add(new Paragraph("ROI Summary"));
            document.add(new Paragraph("Recoverable tax: " + packageResponse.roi().recoverableTax()));
            document.add(new Paragraph("Expected recovered revenue: " + packageResponse.roi().expectedRecoveredRevenue()));
            document.add(new Paragraph("Net benefit: " + packageResponse.roi().netBenefit()));
            document.add(new Paragraph("Included documents"));
            for (PilotDocumentResponse item : packageResponse.documents()) {
                document.add(new Paragraph(item.title() + ": " + item.summary()));
            }
            document.add(new Paragraph("Procurement routes"));
            for (String route : packageResponse.procurementRoutes()) {
                document.add(new Paragraph("- " + route));
            }
            document.close();
            return output.toByteArray();
        } catch (IOException | DocumentException ex) {
            throw new IllegalStateException("Could not export pilot package PDF", ex);
        }
    }

    public byte[] roiWorkbook() {
        RoiSummaryResponse roi = roiSummary();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var summary = workbook.createSheet("ROI Summary");
            Row title = summary.createRow(0);
            title.createCell(0).setCellValue("Phase 16 ROI Calculator");
            String[][] rows = {
                    {"Estimated gap", roi.estimatedGap().toPlainString()},
                    {"Recoverable tax", roi.recoverableTax().toPlainString()},
                    {"Collected amount", roi.collectedAmount().toPlainString()},
                    {"Settlement variance", roi.settlementVariance().toPlainString()},
                    {"Open cases", String.valueOf(roi.openCases())},
                    {"Pilot cost", roi.pilotCost().toPlainString()},
                    {"Expected collection rate", roi.expectedCollectionRate().toPlainString()},
                    {"Expected recovered revenue", ""},
                    {"Net benefit", ""},
                    {"ROI multiple", ""},
                    {"Payback months", ""}
            };
            for (int index = 0; index < rows.length; index++) {
                Row row = summary.createRow(index + 2);
                row.createCell(0).setCellValue(rows[index][0]);
                if (!rows[index][1].isBlank()) {
                    row.createCell(1).setCellValue(new BigDecimal(rows[index][1]).doubleValue());
                }
            }
            summary.getRow(9).createCell(1).setCellFormula("B4*B9");
            summary.getRow(10).createCell(1).setCellFormula("B10-B8");
            summary.getRow(11).createCell(1).setCellFormula("IF(B8=0,0,B10/B8)");
            summary.getRow(12).createCell(1).setCellFormula("IF(B10=0,0,B8/(B10/12))");

            var assumptions = workbook.createSheet("Assumptions");
            assumptions.createRow(0).createCell(0).setCellValue("Editable pilot assumptions");
            String[][] assumptionRows = {
                    {"Pilot length months", "6"},
                    {"Default collection rate", DEFAULT_COLLECTION_RATE.toPlainString()},
                    {"Default pilot cost", DEFAULT_PILOT_COST.toPlainString()},
                    {"Data source", "Synthetic or approved pilot data only"},
                    {"Decision rule", "Officer review required before enforcement"}
            };
            for (int index = 0; index < assumptionRows.length; index++) {
                Row row = assumptions.createRow(index + 2);
                row.createCell(0).setCellValue(assumptionRows[index][0]);
                row.createCell(1).setCellValue(assumptionRows[index][1]);
            }

            var scenarios = workbook.createSheet("Scenarios");
            scenarios.createRow(0).createCell(0).setCellValue("Scenario");
            scenarios.getRow(0).createCell(1).setCellValue("Collection rate");
            scenarios.getRow(0).createCell(2).setCellValue("Recovered revenue");
            scenarios.getRow(0).createCell(3).setCellValue("Net benefit");
            String[][] scenarioRows = {{"Conservative", "0.08"}, {"Base", "0.18"}, {"Strong", "0.30"}};
            for (int index = 0; index < scenarioRows.length; index++) {
                Row row = scenarios.createRow(index + 1);
                row.createCell(0).setCellValue(scenarioRows[index][0]);
                row.createCell(1).setCellValue(Double.parseDouble(scenarioRows[index][1]));
                row.createCell(2).setCellFormula("'ROI Summary'!B4*B" + (index + 2));
                row.createCell(3).setCellFormula("C" + (index + 2) + "-'ROI Summary'!B8");
            }

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                var sheet = workbook.getSheetAt(sheetIndex);
                for (int column = 0; column < 4; column++) {
                    sheet.autoSizeColumn(column);
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not export ROI calculator workbook", ex);
        }
    }

    private BigDecimal singleMoney(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<PilotDocumentResponse> documents() {
        return List.of(
                new PilotDocumentResponse("Pilot Proposal", "Buyer alignment", "docs/phase16/pilot-proposal.md", "Pilot scope, stakeholders, success measures, and delivery plan."),
                new PilotDocumentResponse("Demo Script", "Live walkthrough", "docs/phase16/demo-script.md", "KRA and county demo path from dashboard to evidence pack."),
                new PilotDocumentResponse("Security Overview", "Risk review", "docs/phase16/security-overview.md", "Authentication, authorization, audit logging, privacy, and export controls."),
                new PilotDocumentResponse("Data Processing Overview", "Data governance", "docs/phase16/data-processing-overview.md", "Synthetic-first demo policy and approved pilot data handling."),
                new PilotDocumentResponse("Deployment Overview", "Technical readiness", "docs/phase16/deployment-overview.md", "Local demo environment and controlled pilot deployment pattern."),
                new PilotDocumentResponse("Sample Evidence Packs", "Officer review", "docs/phase16/sample-evidence-packs.md", "Evidence pack structure and sample synthetic case scenarios."),
                new PilotDocumentResponse("Pricing and Procurement", "Commercial readiness", "docs/phase16/pricing-procurement.md", "Pricing model, buyer routes, and procurement notes.")
        );
    }

    private List<DemoUserPersonaResponse> demoUsers() {
        return List.of(
                new DemoUserPersonaResponse("EXECUTIVE", "Commissioner or county finance lead", List.of("View dashboards", "Review ROI", "Approve pilot scope"), "Review revenue gap and pilot ROI."),
                new DemoUserPersonaResponse("OFFICER", "Compliance officer", List.of("Review risk queue", "Open cases", "Generate evidence"), "Move a risk signal into an evidence-backed case."),
                new DemoUserPersonaResponse("ADMIN", "Platform administrator", List.of("Manage users", "Review security", "Export reports"), "Confirm governance controls and export readiness."),
                new DemoUserPersonaResponse("AUDITOR", "Internal audit or data protection reviewer", List.of("Audit logs", "Privacy checklist", "Evidence packs"), "Verify no real taxpayer data is used without approval.")
        );
    }

    private List<String> sampleDashboards() {
        return List.of(
                "Executive revenue gap dashboard",
                "Sector risk dashboard",
                "Regional risk dashboard",
                "Audit pipeline dashboard",
                "Settlement variance dashboard",
                "Pilot ROI calculator"
        );
    }

    private List<String> procurementRoutes() {
        return List.of(
                "Controlled proof of concept under innovation or ICT sandbox approval.",
                "County own-source revenue pilot with implementation services.",
                "Integrator-led deployment where the platform is the intelligence layer.",
                "Annual government software license plus implementation and support."
        );
    }
}
