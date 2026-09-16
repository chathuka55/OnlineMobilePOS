package com.possaas.reporting.web;

import com.possaas.reporting.api.dto.ReportingDtos.DailySummaryResponse;
import com.possaas.reporting.api.dto.ReportingDtos.MonthlySummaryResponse;
import com.possaas.reporting.service.ReportService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily-summary")
    public DailySummaryResponse dailySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.dailySummary(date);
    }

    @GetMapping("/monthly-summary")
    public MonthlySummaryResponse monthlySummary(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return reportService.monthlySummary(year, month);
    }

    @GetMapping("/bills/{billId}/invoice.pdf")
    public ResponseEntity<byte[]> billInvoicePdf(@PathVariable UUID billId) {
        byte[] pdf = reportService.generateBillInvoicePdf(billId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"bill-" + billId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
