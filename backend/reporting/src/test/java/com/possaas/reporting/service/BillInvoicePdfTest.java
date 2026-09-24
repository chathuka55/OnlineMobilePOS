package com.possaas.reporting.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Renders the bill invoice PDF (both page sizes, all logo layouts) end-to-end rather
 * than just compiling the jrxml - a template that compiles can still throw at fill/export
 * time, which is exactly how the OpenPDF/itext dependency mismatch went undetected here.
 */
class BillInvoicePdfTest {

    // A minimal valid PNG (1x1 red pixel).
    private static final byte[] TEST_LOGO = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Test
    void a4AllThreeLogoScenarios() throws Exception {
        renderAll("reports/bill_invoice.jrxml");
    }

    @Test
    void halfA4AllThreeLogoScenarios() throws Exception {
        renderAll("reports/bill_invoice_half_a4.jrxml");
    }

    private void renderAll(String template) throws Exception {
        JasperReport report;
        try (var in = new ClassPathResource(template).getInputStream()) {
            report = JasperCompileManager.compileReport(in);
        }

        assertRenders(report, "SIDE", TEST_LOGO, "TAX INVOICE", "side layout with logo");
        assertRenders(report, "CENTERED", TEST_LOGO, "TAX INVOICE", "centered layout with logo");
        assertRenders(report, "SIDE", null, "TAX INVOICE", "no logo at all");
        // A shop that isn't VAT-registered must not issue a document headed "Tax Invoice".
        assertRenders(report, "SIDE", null, "INVOICE", "unregistered shop");
    }

    private void assertRenders(JasperReport report, String layout, byte[] logo,
                               String documentTitle, String label) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("BILL_NUMBER", "INV-TEST-0001");
        params.put("CUSTOMER_NAME", "Test Customer");
        params.put("CURRENCY", "LKR");
        params.put("SUBTOTAL", new BigDecimal("1000.00"));
        params.put("TAX_TOTAL", new BigDecimal("0.00"));
        params.put("DISCOUNT_TOTAL", new BigDecimal("0.00"));
        params.put("GRAND_TOTAL", new BigDecimal("1000.00"));
        params.put("BILLED_AT", "2026-01-01T00:00:00Z");
        params.put("BUSINESS_NAME", "Test Shop Pvt Ltd");
        params.put("OUTLET_ADDRESS", "123 Main Street, Colombo");
        params.put("OUTLET_CONTACT", "Tel: 0771234567   shop@example.com");
        params.put("LOGO_IMAGE", logo == null ? null : new ByteArrayInputStream(logo));
        params.put("LOGO_LAYOUT", layout);
        params.put("DOCUMENT_TITLE", documentTitle);
        params.put("VAT_TIN", "TAX INVOICE".equals(documentTitle) ? "VAT No: 123456789" : "");

        var line = new ReportService.BillLineRow();
        line.setLineNumber((short) 1);
        line.setItemSku("SKU-1");
        line.setItemName("Test Item");
        line.setQuantity(new BigDecimal("1.000"));
        line.setUnitPrice(new BigDecimal("1000.00"));
        line.setNetAmount(new BigDecimal("1000.00"));

        JasperPrint print = JasperFillManager.fillReport(
                report, params, new JRBeanCollectionDataSource(List.of(line)));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertNotNull(pdf, label + ": PDF bytes were null");
        assertTrue(pdf.length > 500, label + ": PDF suspiciously small (" + pdf.length + " bytes)");
        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"),
                label + ": output doesn't look like a PDF");

        // Checked on the rendered page rather than the PDF bytes, which are compressed.
        List<String> texts = renderedText(print);
        assertTrue(texts.contains(documentTitle),
                label + ": expected the document to be headed \"" + documentTitle
                        + "\" but the rendered text was " + texts);
        if ("INVOICE".equals(documentTitle)) {
            assertTrue(texts.stream().noneMatch(t -> t.contains("TAX INVOICE")),
                    label + ": an unregistered shop must not print \"TAX INVOICE\"");
        }
    }

    /** Every piece of text actually laid out on the page. */
    private static List<String> renderedText(JasperPrint print) {
        List<String> out = new java.util.ArrayList<>();
        for (var page : print.getPages()) {
            for (var element : page.getElements()) {
                if (element instanceof net.sf.jasperreports.engine.JRPrintText text) {
                    String value = text.getFullText();
                    if (value != null && !value.isBlank()) {
                        out.add(value.trim());
                    }
                }
            }
        }
        return out;
    }
}
