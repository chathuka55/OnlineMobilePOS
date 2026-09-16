package com.possaas.reporting.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.tenant.TenantContext;
import com.possaas.reporting.api.dto.ReportingDtos.DailySummaryResponse;
import com.possaas.reporting.api.dto.ReportingDtos.MonthlySummaryResponse;
import com.possaas.sales.domain.Bill;
import com.possaas.sales.domain.BillLine;
import com.possaas.sales.repository.BillRepository;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.repository.TenantRepository;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final JdbcTemplate jdbcTemplate;
    private final BillRepository billRepository;
    private final TenantRepository tenantRepository;

    public ReportService(JdbcTemplate jdbcTemplate,
                         BillRepository billRepository,
                         TenantRepository tenantRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.billRepository = billRepository;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public DailySummaryResponse dailySummary(LocalDate date) {
        TenantContext.requireTenantId();
        ZoneId zone = zone();
        LocalDate day = date == null ? LocalDate.now(zone) : date;
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        return loadDaily(day, from, to, currency());
    }

    @Transactional(readOnly = true)
    public MonthlySummaryResponse monthlySummary(Integer year, Integer month) {
        TenantContext.requireTenantId();
        ZoneId zone = zone();
        YearMonth ym = year == null || month == null
                ? YearMonth.now(zone)
                : YearMonth.of(year, month);
        Instant from = ym.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        String currency = currency();

        BigDecimal salesTotal = queryDecimal(
                """
                        SELECT COALESCE(SUM(grand_total), 0) FROM bills
                         WHERE tenant_id = ? AND billed_at >= ? AND billed_at < ?
                           AND status <> 'VOIDED'
                        """,
                TenantContext.requireTenantId(), from, to);
        Long billCount = queryLong(
                """
                        SELECT COUNT(*) FROM bills
                         WHERE tenant_id = ? AND billed_at >= ? AND billed_at < ?
                           AND status <> 'VOIDED'
                        """,
                TenantContext.requireTenantId(), from, to);
        BigDecimal refundTotal = queryDecimal(
                """
                        SELECT COALESCE(SUM(refund_amount), 0) FROM refunds
                         WHERE tenant_id = ? AND refunded_at >= ? AND refunded_at < ?
                        """,
                TenantContext.requireTenantId(), from, to);
        Long refundCount = queryLong(
                """
                        SELECT COUNT(*) FROM refunds
                         WHERE tenant_id = ? AND refunded_at >= ? AND refunded_at < ?
                        """,
                TenantContext.requireTenantId(), from, to);

        List<DailySummaryResponse> days = new ArrayList<>();
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate day = ym.atDay(d);
            if (day.isAfter(LocalDate.now(zone))) {
                break;
            }
            Instant dayFrom = day.atStartOfDay(zone).toInstant();
            Instant dayTo = day.plusDays(1).atStartOfDay(zone).toInstant();
            days.add(loadDaily(day, dayFrom, dayTo, currency));
        }

        BigDecimal net = salesTotal.subtract(refundTotal);
        return new MonthlySummaryResponse(
                ym.getYear(),
                ym.getMonthValue(),
                salesTotal,
                billCount == null ? 0L : billCount,
                refundTotal,
                refundCount == null ? 0L : refundCount,
                net,
                days,
                currency);
    }

    @Transactional(readOnly = true)
    public byte[] generateBillInvoicePdf(UUID billId) {
        Bill bill = billRepository.findByIdWithLines(billId)
                .orElseThrow(() -> ApiException.notFound("Bill", billId));

        List<BillLineRow> lines = new ArrayList<>();
        for (BillLine line : bill.getLines()) {
            BillLineRow row = new BillLineRow();
            row.setLineNumber(line.getLineNumber());
            row.setItemSku(line.getItemSku());
            row.setItemName(line.getItemName());
            row.setQuantity(line.getQuantity());
            row.setUnitPrice(line.getUnitPrice());
            row.setNetAmount(line.getNetAmount());
            lines.add(row);
        }
        if (lines.isEmpty()) {
            BillLineRow empty = new BillLineRow();
            empty.setLineNumber((short) 1);
            empty.setItemSku("-");
            empty.setItemName("No lines");
            empty.setQuantity(BigDecimal.ZERO);
            empty.setUnitPrice(BigDecimal.ZERO);
            empty.setNetAmount(BigDecimal.ZERO);
            lines.add(empty);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("BILL_NUMBER", bill.getBillNumber());
        params.put("CUSTOMER_NAME", bill.getCustomerName());
        params.put("CURRENCY", bill.getCurrency());
        params.put("SUBTOTAL", bill.getSubtotal());
        params.put("TAX_TOTAL", bill.getTaxTotal());
        params.put("DISCOUNT_TOTAL", bill.getBillDiscountAmount().add(bill.getLineDiscountTotal()));
        params.put("GRAND_TOTAL", bill.getGrandTotal());
        params.put("BILLED_AT", bill.getBilledAt() == null ? "" : bill.getBilledAt().toString());

        try (InputStream template = new ClassPathResource("reports/bill_invoice.jrxml").getInputStream()) {
            JasperReport report = JasperCompileManager.compileReport(template);
            JasperPrint print = JasperFillManager.fillReport(
                    report, params, new JRBeanCollectionDataSource(lines));
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Failed to generate bill invoice PDF", ex);
        }
    }

    private DailySummaryResponse loadDaily(LocalDate day, Instant from, Instant to, String currency) {
        UUID tenantId = TenantContext.requireTenantId();
        BigDecimal salesTotal = queryDecimal(
                """
                        SELECT COALESCE(SUM(grand_total), 0) FROM bills
                         WHERE tenant_id = ? AND billed_at >= ? AND billed_at < ?
                           AND status <> 'VOIDED'
                        """,
                tenantId, from, to);
        Long billCount = queryLong(
                """
                        SELECT COUNT(*) FROM bills
                         WHERE tenant_id = ? AND billed_at >= ? AND billed_at < ?
                           AND status <> 'VOIDED'
                        """,
                tenantId, from, to);
        BigDecimal refundTotal = queryDecimal(
                """
                        SELECT COALESCE(SUM(refund_amount), 0) FROM refunds
                         WHERE tenant_id = ? AND refunded_at >= ? AND refunded_at < ?
                        """,
                tenantId, from, to);
        Long refundCount = queryLong(
                """
                        SELECT COUNT(*) FROM refunds
                         WHERE tenant_id = ? AND refunded_at >= ? AND refunded_at < ?
                        """,
                tenantId, from, to);
        return new DailySummaryResponse(
                day,
                salesTotal,
                billCount == null ? 0L : billCount,
                refundTotal,
                refundCount == null ? 0L : refundCount,
                salesTotal.subtract(refundTotal),
                currency);
    }

    private BigDecimal queryDecimal(String sql, Object... args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long queryLong(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private ZoneId zone() {
        return tenantRepository.findById(TenantContext.requireTenantId())
                .map(Tenant::getTimeZone)
                .map(ZoneId::of)
                .orElse(ZoneId.of("Asia/Colombo"));
    }

    private String currency() {
        return tenantRepository.findById(TenantContext.requireTenantId())
                .map(Tenant::getDefaultCurrency)
                .orElse("LKR");
    }

    /** Bean fields consumed by {@code bill_invoice.jrxml}. */
    public static class BillLineRow {
        private short lineNumber;
        private String itemSku;
        private String itemName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal netAmount;

        public short getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(short lineNumber) {
            this.lineNumber = lineNumber;
        }

        public String getItemSku() {
            return itemSku;
        }

        public void setItemSku(String itemSku) {
            this.itemSku = itemSku;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public BigDecimal getNetAmount() {
            return netAmount;
        }

        public void setNetAmount(BigDecimal netAmount) {
            this.netAmount = netAmount;
        }
    }
}
