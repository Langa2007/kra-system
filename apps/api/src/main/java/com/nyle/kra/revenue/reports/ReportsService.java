package com.nyle.kra.revenue.reports;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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
public class ReportsService {

    private final JdbcTemplate jdbcTemplate;

    public ReportsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TaxGapBySectorResponse> taxGapBySector() {
        refreshViews();
        return jdbcTemplate.query("""
                SELECT sector_code, sector_name, tax_head, estimate_count, taxpayer_count,
                       estimated_gap, estimated_recoverable_tax, estimated_total_due, average_confidence
                FROM mv_tax_gap_by_sector
                ORDER BY estimated_recoverable_tax DESC, sector_name ASC, tax_head ASC
                """, this::sector);
    }

    public List<TaxGapByRegionResponse> taxGapByRegion() {
        refreshViews();
        return jdbcTemplate.query("""
                SELECT region, tax_head, estimate_count, taxpayer_count,
                       estimated_gap, estimated_recoverable_tax, estimated_total_due, average_confidence
                FROM mv_tax_gap_by_region
                ORDER BY estimated_recoverable_tax DESC, region ASC, tax_head ASC
                """, this::region);
    }

    public List<OfficerProductivityResponse> officerProductivity() {
        return jdbcTemplate.query("""
                SELECT COALESCE(c.assigned_to::text, 'UNASSIGNED') AS officer_id,
                       COALESCE(au.full_name, 'Unassigned') AS officer_name,
                       count(c.id) AS assigned_cases,
                       count(c.id) FILTER (WHERE c.status <> 'CLOSED') AS open_cases,
                       count(c.id) FILTER (WHERE c.status = 'CLOSED') AS closed_cases,
                       COALESCE(sum(rr.assessed_amount), 0) AS assessed_amount,
                       COALESCE(sum(rr.agreed_amount), 0) AS agreed_amount,
                       COALESCE(sum(rr.collected_amount), 0) AS collected_amount,
                       COALESCE(round(avg(EXTRACT(epoch FROM (COALESCE(c.closed_at, now()) - c.opened_at)) / 86400), 2), 0) AS average_case_age_days
                FROM cases c
                LEFT JOIN app_users au ON au.id = c.assigned_to
                LEFT JOIN recovery_records rr ON rr.case_id = c.id
                GROUP BY COALESCE(c.assigned_to::text, 'UNASSIGNED'), COALESCE(au.full_name, 'Unassigned')
                ORDER BY collected_amount DESC, assigned_cases DESC, officer_name ASC
                """, (rs, rowNum) -> new OfficerProductivityResponse(
                rs.getString("officer_id"),
                rs.getString("officer_name"),
                rs.getLong("assigned_cases"),
                rs.getLong("open_cases"),
                rs.getLong("closed_cases"),
                money(rs, "assessed_amount"),
                money(rs, "agreed_amount"),
                money(rs, "collected_amount"),
                money(rs, "average_case_age_days")
        ));
    }

    public List<RevenueRecoveryResponse> revenueRecovery() {
        return jdbcTemplate.query("""
                SELECT date_trunc('month', COALESCE(rr.collection_date, rr.created_at::date))::date AS period,
                       COALESCE(rs.tax_head, 'UNSPECIFIED') AS tax_head,
                       count(rr.id) AS recovery_records,
                       COALESCE(sum(rr.assessed_amount), 0) AS assessed_amount,
                       COALESCE(sum(rr.agreed_amount), 0) AS agreed_amount,
                       COALESCE(sum(rr.collected_amount), 0) AS collected_amount
                FROM recovery_records rr
                JOIN cases c ON c.id = rr.case_id
                LEFT JOIN risk_signals rs ON rs.id = c.risk_signal_id
                GROUP BY date_trunc('month', COALESCE(rr.collection_date, rr.created_at::date))::date,
                         COALESCE(rs.tax_head, 'UNSPECIFIED')
                ORDER BY period DESC, collected_amount DESC, tax_head ASC
                """, (rs, rowNum) -> new RevenueRecoveryResponse(
                rs.getObject("period", LocalDate.class),
                rs.getString("tax_head"),
                rs.getLong("recovery_records"),
                money(rs, "assessed_amount"),
                money(rs, "agreed_amount"),
                money(rs, "collected_amount")
        ));
    }

    public List<AuditPipelineResponse> auditPipeline() {
        return jdbcTemplate.query("""
                SELECT c.status,
                       count(c.id) AS case_count,
                       COALESCE(sum(c.estimated_recoverable_amount), 0) AS estimated_recoverable_amount,
                       COALESCE(sum(rr.assessed_amount), 0) AS assessed_amount,
                       COALESCE(sum(rr.collected_amount), 0) AS collected_amount
                FROM cases c
                LEFT JOIN recovery_records rr ON rr.case_id = c.id
                GROUP BY c.status
                ORDER BY case_count DESC, c.status ASC
                """, (rs, rowNum) -> new AuditPipelineResponse(
                rs.getString("status"),
                rs.getLong("case_count"),
                money(rs, "estimated_recoverable_amount"),
                money(rs, "assessed_amount"),
                money(rs, "collected_amount")
        ));
    }

    public byte[] exportTaxGapBySector(ReportExportFormat format) {
        List<TaxGapBySectorResponse> rows = taxGapBySector();
        return switch (format) {
            case CSV -> csv(rows);
            case XLSX -> xlsx(rows);
            case PDF -> pdf(rows);
        };
    }

    private void refreshViews() {
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_tax_gap_by_sector");
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW mv_tax_gap_by_region");
    }

    private TaxGapBySectorResponse sector(ResultSet rs, int rowNum) throws SQLException {
        return new TaxGapBySectorResponse(
                rs.getString("sector_code"),
                rs.getString("sector_name"),
                rs.getString("tax_head"),
                rs.getInt("estimate_count"),
                rs.getInt("taxpayer_count"),
                money(rs, "estimated_gap"),
                money(rs, "estimated_recoverable_tax"),
                money(rs, "estimated_total_due"),
                money(rs, "average_confidence")
        );
    }

    private TaxGapByRegionResponse region(ResultSet rs, int rowNum) throws SQLException {
        return new TaxGapByRegionResponse(
                rs.getString("region"),
                rs.getString("tax_head"),
                rs.getInt("estimate_count"),
                rs.getInt("taxpayer_count"),
                money(rs, "estimated_gap"),
                money(rs, "estimated_recoverable_tax"),
                money(rs, "estimated_total_due"),
                money(rs, "average_confidence")
        );
    }

    private BigDecimal money(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private byte[] csv(List<TaxGapBySectorResponse> rows) {
        StringBuilder csv = new StringBuilder("sectorCode,sectorName,taxHead,estimateCount,taxpayerCount,estimatedGap,estimatedRecoverableTax,estimatedTotalDue,averageConfidence\n");
        for (TaxGapBySectorResponse row : rows) {
            csv.append(escape(row.sectorCode())).append(',')
                    .append(escape(row.sectorName())).append(',')
                    .append(escape(row.taxHead())).append(',')
                    .append(row.estimateCount()).append(',')
                    .append(row.taxpayerCount()).append(',')
                    .append(row.estimatedGap()).append(',')
                    .append(row.estimatedRecoverableTax()).append(',')
                    .append(row.estimatedTotalDue()).append(',')
                    .append(row.averageConfidence()).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] xlsx(List<TaxGapBySectorResponse> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Tax Gap By Sector");
            Row header = sheet.createRow(0);
            String[] columns = {"Sector Code", "Sector Name", "Tax Head", "Estimates", "Taxpayers", "Gap", "Recoverable", "Total Due", "Confidence"};
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index]);
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                TaxGapBySectorResponse item = rows.get(rowIndex);
                Row row = sheet.createRow(rowIndex + 1);
                row.createCell(0).setCellValue(item.sectorCode());
                row.createCell(1).setCellValue(item.sectorName());
                row.createCell(2).setCellValue(item.taxHead());
                row.createCell(3).setCellValue(item.estimateCount());
                row.createCell(4).setCellValue(item.taxpayerCount());
                row.createCell(5).setCellValue(item.estimatedGap().doubleValue());
                row.createCell(6).setCellValue(item.estimatedRecoverableTax().doubleValue());
                row.createCell(7).setCellValue(item.estimatedTotalDue().doubleValue());
                row.createCell(8).setCellValue(item.averageConfidence().doubleValue());
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not export report as XLSX", ex);
        }
    }

    private byte[] pdf(List<TaxGapBySectorResponse> rows) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("Tax Gap By Sector"));
            for (TaxGapBySectorResponse row : rows) {
                document.add(new Paragraph("%s / %s / %s: gap %s, recoverable %s".formatted(
                        row.sectorCode(),
                        row.sectorName(),
                        row.taxHead(),
                        row.estimatedGap(),
                        row.estimatedRecoverableTax()
                )));
            }
            document.close();
            return output.toByteArray();
        } catch (IOException | DocumentException ex) {
            throw new IllegalStateException("Could not export report as PDF", ex);
        }
    }

    private String escape(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
