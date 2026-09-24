package com.possaas.reporting.web;

import com.possaas.reporting.api.dto.ReportingDtos.DailySummaryResponse;
import com.possaas.reporting.api.dto.ReportingDtos.ProfitReportResponse;
import com.possaas.reporting.api.dto.ReportingDtos.StockValuationResponse;
import com.possaas.reporting.api.dto.ReportingDtos.VatOutputResponse;
import com.possaas.reporting.api.dto.ReportingDtos.MonthlySummaryResponse;
import com.possaas.reporting.api.dto.ReportingDtos.SalesRangeResponse;
import com.possaas.reporting.api.dto.ReportingDtos.TopCustomerRow;
import com.possaas.reporting.service.ReportService;
import java.time.LocalDate;
import java.util.List;
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

    @GetMapping("/sales")
    public SalesRangeResponse sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.salesRange(from, to);
    }

    @GetMapping("/customers")
    public List<TopCustomerRow> customers(
            @RequestParam(required = false, defaultValue = "50") int size) {
        return reportService.topCustomers(size);
    }

    /** VAT charged on sales in a period - what a VAT return is filed from. */
    @GetMapping("/vat-output")
    public VatOutputResponse vatOutput(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.vatOutput(from, to);
    }

    /** Gross profit per sold line, at each serialised unit's own cost. */
    @GetMapping("/profit")
    public ProfitReportResponse profit(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        return reportService.profitByLine(from, to, limit);
    }

    @GetMapping("/stock-valuation")
    public StockValuationResponse stockValuation() {
        return reportService.stockValuation();
    }

    @GetMapping("/bills/{billId}/invoice.pdf")
    public ResponseEntity<byte[]> billInvoicePdf(
            @PathVariable UUID billId,
            @RequestParam(required = false, defaultValue = "A4") String size) {
        byte[] pdf = reportService.generateBillInvoicePdf(billId, size);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"bill-" + billId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
