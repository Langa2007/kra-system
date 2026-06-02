package com.nyle.kra.revenue.reports;

public enum ReportExportFormat {
    CSV("text/csv", "tax-gap-by-sector.csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "tax-gap-by-sector.xlsx"),
    PDF("application/pdf", "tax-gap-by-sector.pdf");

    private final String mediaType;
    private final String fileName;

    ReportExportFormat(String mediaType, String fileName) {
        this.mediaType = mediaType;
        this.fileName = fileName;
    }

    public String mediaType() {
        return mediaType;
    }

    public String fileName() {
        return fileName;
    }
}

