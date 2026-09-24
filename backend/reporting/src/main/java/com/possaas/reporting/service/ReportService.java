package com.possaas.reporting.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.jdbc.SqlArgs;
import com.possaas.common.money.Money;
import com.possaas.common.tenant.TenantContext;
import com.possaas.reporting.api.dto.ReportingDtos.DailySummaryResponse;
import com.possaas.reporting.api.dto.ReportingDtos.ProfitLineRow;
import com.possaas.reporting.api.dto.ReportingDtos.ProfitReportResponse;
import com.possaas.reporting.api.dto.ReportingDtos.StockValuationResponse;
import com.possaas.reporting.api.dto.ReportingDtos.StockValuationRow;
import com.possaas.reporting.api.dto.ReportingDtos.VatOutputResponse;
import com.possaas.reporting.api.dto.ReportingDtos.VatRateRow;
import com.possaas.reporting.api.dto.ReportingDtos.MonthlySummaryResponse;
import com.possaas.reporting.api.dto.ReportingDtos.SalesDayRow;
import com.possaas.reporting.api.dto.ReportingDtos.SalesRangeResponse;
import com.possaas.reporting.api.dto.ReportingDtos.TopCustomerRow;
import com.possaas.sales.domain.Bill;
import com.possaas.sales.domain.BillLine;
import com.possaas.sales.repository.BillRepository;
import com.possaas.tenancy.domain.Outlet;
import com.possaas.tenancy.domain.SettingKeys;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.repository.OutletRepository;
import com.possaas.tenancy.repository.TenantRepository;
import com.possaas.tenancy.service.SettingsService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Base64;
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
    private final OutletRepository outletRepository;
    private final SettingsService settingsService;

    public ReportService(JdbcTemplate jdbcTemplate,
                         BillRepository billRepository,
                         TenantRepository tenantRepository,
                         OutletRepository outletRepository,
                         SettingsService settingsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.billRepository = billRepository;
        this.tenantRepository = tenantRepository;
        this.outletRepository = outletRepository;
        this.settingsService = settingsService;
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
    public SalesRangeResponse salesRange(LocalDate from, LocalDate to) {
        TenantContext.requireTenantId();
        ZoneId zone = zone();
        String currency = currency();
        LocalDate rangeTo = to == null ? LocalDate.now(zone) : to;
        LocalDate rangeFrom = from == null ? rangeTo.minusDays(29) : from;
        if (rangeFrom.isAfter(rangeTo)) {
            LocalDate swap = rangeFrom;
            rangeFrom = rangeTo;
            rangeTo = swap;
        }
        if (rangeFrom.isBefore(rangeTo.minusDays(366))) {
            rangeFrom = rangeTo.minusDays(366);
        }

        List<SalesDayRow> days = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalRefunds = BigDecimal.ZERO;
        long totalBills = 0;
        for (LocalDate day = rangeFrom; !day.isAfter(rangeTo); day = day.plusDays(1)) {
            Instant dayFrom = day.atStartOfDay(zone).toInstant();
            Instant dayTo = day.plusDays(1).atStartOfDay(zone).toInstant();
            DailySummaryResponse summary = loadDaily(day, dayFrom, dayTo, currency);
            days.add(new SalesDayRow(day, summary.billCount(), summary.salesTotal(),
                    summary.refundTotal(), summary.netSales()));
            totalRevenue = totalRevenue.add(summary.salesTotal());
            totalRefunds = totalRefunds.add(summary.refundTotal());
            totalBills += summary.billCount();
        }

        BigDecimal averageBillValue = totalBills == 0
                ? BigDecimal.ZERO
                : totalRevenue.divide(BigDecimal.valueOf(totalBills), 2, java.math.RoundingMode.HALF_UP);

        return new SalesRangeResponse(rangeFrom, rangeTo, totalRevenue, totalBills,
                averageBillValue, totalRefunds, days, currency);
    }

    @Transactional(readOnly = true)
    public List<TopCustomerRow> topCustomers(int size) {
        UUID tenantId = TenantContext.requireTenantId();
        int limit = Math.max(1, Math.min(size, 200));
        return jdbcTemplate.query(
                """
                        SELECT c.id, c.display_name, c.customer_type,
                               c.lifetime_sales, c.outstanding_amount,
                               (SELECT MAX(b.billed_at) FROM bills b
                                 WHERE b.customer_id = c.id AND b.tenant_id = c.tenant_id) AS last_purchase_at
                          FROM customers c
                         WHERE c.tenant_id = ? AND c.deleted_at IS NULL
                         ORDER BY c.lifetime_sales DESC
                         LIMIT ?
                        """,
                (rs, rowNum) -> new TopCustomerRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("display_name"),
                        rs.getString("customer_type"),
                        rs.getBigDecimal("lifetime_sales"),
                        rs.getBigDecimal("outstanding_amount"),
                        rs.getTimestamp("last_purchase_at") == null
                                ? null : rs.getTimestamp("last_purchase_at").toInstant()),
                tenantId, limit);
    }

    /** "A4" (default) or "HALF_A4". Anything else falls back to A4. */
    @Transactional(readOnly = true)
    public byte[] generateBillInvoicePdf(UUID billId, String size) {
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

        Outlet outlet = bill.getOutletId() == null ? null
                : outletRepository.findById(bill.getOutletId()).orElse(null);

        Map<String, Object> params = new HashMap<>();
        params.put("BILL_NUMBER", bill.getBillNumber());
        params.put("CUSTOMER_NAME", bill.getCustomerName());
        params.put("CURRENCY", bill.getCurrency());
        params.put("SUBTOTAL", bill.getSubtotal());
        params.put("TAX_TOTAL", bill.getTaxTotal());
        params.put("DISCOUNT_TOTAL", bill.getBillDiscountAmount().add(bill.getLineDiscountTotal()));
        params.put("GRAND_TOTAL", bill.getGrandTotal());
        params.put("BILLED_AT", bill.getBilledAt() == null ? "" : bill.getBilledAt().toString());
        Tenant tenant = tenantRepository.findById(TenantContext.requireTenantId()).orElse(null);
        params.put("BUSINESS_NAME", tenant == null ? "" : tenant.getBusinessName());
        // Only a VAT-registered shop may head a document "Tax Invoice"; doing so
        // otherwise claims a registration the shop does not hold.
        boolean vatRegistered = tenant != null && tenant.isVatRegistered();
        params.put("DOCUMENT_TITLE", vatRegistered ? "TAX INVOICE" : "INVOICE");
        params.put("VAT_TIN", vatRegistered && tenant.getTaxIdentifier() != null
                ? "VAT No: " + tenant.getTaxIdentifier()
                : "");
        params.put("OUTLET_ADDRESS", outletAddress(outlet));
        params.put("OUTLET_CONTACT", outletContact(outlet));
        params.put("LOGO_IMAGE", logoStream(outlet));
        params.put("LOGO_LAYOUT", settingsService.getString(SettingKeys.RECEIPT_LOGO_LAYOUT, "SIDE"));

        String template = "HALF_A4".equalsIgnoreCase(size)
                ? "reports/bill_invoice_half_a4.jrxml"
                : "reports/bill_invoice.jrxml";

        try (InputStream templateStream = new ClassPathResource(template).getInputStream()) {
            JasperReport report = JasperCompileManager.compileReport(templateStream);
            JasperPrint print = JasperFillManager.fillReport(
                    report, params, new JRBeanCollectionDataSource(lines));
            return JasperExportManager.exportReportToPdf(print);
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Failed to generate bill invoice PDF", ex);
        }
    }

    private String outletAddress(Outlet outlet) {
        if (outlet == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (outlet.getAddressLine1() != null && !outlet.getAddressLine1().isBlank()) {
            sb.append(outlet.getAddressLine1());
        }
        if (outlet.getCity() != null && !outlet.getCity().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(outlet.getCity());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String outletContact(Outlet outlet) {
        if (outlet == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (outlet.getPhonePrimary() != null && !outlet.getPhonePrimary().isBlank()) {
            sb.append("Tel: ").append(outlet.getPhonePrimary());
        }
        if (outlet.getEmail() != null && !outlet.getEmail().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("   ");
            }
            sb.append(outlet.getEmail());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * Decodes an outlet's {@code data:image/...;base64,...} logo into raw bytes for
     * Jasper's image element. Returns null (not an error) when there's no outlet or
     * no logo set - the jrxml's onErrorType="Blank" then just omits the image.
     */
    private InputStream logoStream(Outlet outlet) {
        String dataUrl = outlet == null ? null : outlet.getLogoDataUrl();
        if (dataUrl == null) {
            return null;
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
            return new ByteArrayInputStream(bytes);
        } catch (IllegalArgumentException ex) {
            return null;
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
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, jdbcArgs(args));
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long queryLong(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, jdbcArgs(args));
    }

    private static Object[] jdbcArgs(Object... args) {
        return SqlArgs.of(args);
    }

    /**
     * VAT charged on sales in a period.
     *
     * <p>Every figure is summed from the values stored on the bill lines, never
     * recomputed from the rate. A report that recalculates can disagree with the
     * document the customer is holding; one that adds up what was printed cannot.
     * Voided bills are excluded - they were reversed, so no VAT was charged.
     */
    @Transactional(readOnly = true)
    public VatOutputResponse vatOutput(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.requireTenantId();
        ZoneId zone = zone();
        LocalDate rangeTo = to == null ? LocalDate.now(zone) : to;
        LocalDate rangeFrom = from == null ? rangeTo.withDayOfMonth(1) : from;
        Instant fromInstant = rangeFrom.atStartOfDay(zone).toInstant();
        Instant toInstant = rangeTo.plusDays(1).atStartOfDay(zone).toInstant();

        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        boolean vatRegistered = tenant != null && tenant.isVatRegistered();

        List<VatRateRow> byRate = jdbcTemplate.query(
                "SELECT bl.tax_rate_percent,"
                        + " COALESCE(SUM(bl.line_total - bl.tax_amount), 0) AS taxable,"
                        + " COALESCE(SUM(bl.tax_amount), 0) AS vat"
                        + " FROM bill_lines bl JOIN bills b ON b.id = bl.bill_id"
                        + " WHERE b.billed_at >= ? AND b.billed_at < ?"
                        + " AND b.status <> 'VOIDED' AND bl.tax_amount <> 0"
                        + " GROUP BY bl.tax_rate_percent ORDER BY bl.tax_rate_percent DESC",
                (rs, rowNum) -> new VatRateRow(
                        rs.getBigDecimal("tax_rate_percent"),
                        Money.of(rs.getBigDecimal("taxable")),
                        Money.of(rs.getBigDecimal("vat"))),
                Timestamp.from(fromInstant), Timestamp.from(toInstant));

        Map<String, Object> totals = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS bill_count,"
                        + " COALESCE(SUM(b.grand_total), 0) AS gross,"
                        + " COALESCE(SUM(b.tax_total), 0) AS vat"
                        + " FROM bills b WHERE b.billed_at >= ? AND b.billed_at < ?"
                        + " AND b.status <> 'VOIDED'",
                Timestamp.from(fromInstant), Timestamp.from(toInstant));

        // Refunds carry no tax column of their own, so the VAT reversed is derived
        // from the rate on the bill line being returned - the same 18/118 split the
        // original sale used. Deriving is a compromise: every other figure here is
        // summed from stored values so it ties to the printed document.
        Map<String, Object> refunds = jdbcTemplate.queryForMap(
                "SELECT COALESCE(SUM(rl.line_total), 0) AS gross,"
                        + " COALESCE(SUM(CASE WHEN bl.tax_inclusive"
                        + "   THEN rl.line_total * bl.tax_rate_percent / (100 + bl.tax_rate_percent)"
                        + "   ELSE rl.line_total * bl.tax_rate_percent / 100 END), 0) AS vat"
                        + " FROM refund_lines rl"
                        + " JOIN refunds r ON r.id = rl.refund_id"
                        + " LEFT JOIN bill_lines bl ON bl.id = rl.bill_line_id"
                        + " WHERE r.refunded_at >= ? AND r.refunded_at < ?",
                Timestamp.from(fromInstant), Timestamp.from(toInstant));

        BigDecimal grossSales = Money.of((BigDecimal) totals.get("gross"));
        BigDecimal vatOutput = Money.of((BigDecimal) totals.get("vat"));
        BigDecimal refundedGross = Money.of((BigDecimal) refunds.get("gross"));
        BigDecimal refundedVat = Money.of((BigDecimal) refunds.get("vat"));

        // Taxable value is the value of the supplies VAT was actually charged on.
        // Deriving it as gross minus VAT would sweep zero-rated and exempt sales into
        // it, overstating the taxable base on the return. Anything left over after
        // taxable value and its VAT is reported separately as zero-rated.
        BigDecimal taxableValue = Money.ZERO;
        for (VatRateRow row : byRate) {
            taxableValue = Money.add(taxableValue, row.taxableValue());
        }
        BigDecimal zeroRatedValue = Money.subtract(
                grossSales, Money.add(taxableValue, vatOutput));

        return new VatOutputResponse(
                rangeFrom, rangeTo, vatRegistered,
                tenant == null ? null : tenant.getTaxIdentifier(),
                ((Number) totals.get("bill_count")).longValue(),
                grossSales,
                taxableValue,
                zeroRatedValue,
                vatOutput,
                refundedGross,
                refundedVat,
                Money.subtract(vatOutput, refundedVat),
                currency(),
                byRate);
    }

    /**
     * Gross profit per sold line, using each serialised unit's own purchase cost
     * where there is one. Two identical handsets bought at different prices have
     * different margins, which an item-level average cost would hide.
     */
    @Transactional(readOnly = true)
    public ProfitReportResponse profitByLine(LocalDate from, LocalDate to, int limit) {
        TenantContext.requireTenantId();
        ZoneId zone = zone();
        LocalDate rangeTo = to == null ? LocalDate.now(zone) : to;
        LocalDate rangeFrom = from == null ? rangeTo.minusDays(29) : from;
        Instant fromInstant = rangeFrom.atStartOfDay(zone).toInstant();
        Instant toInstant = rangeTo.plusDays(1).atStartOfDay(zone).toInstant();
        int capped = Math.max(1, Math.min(limit, 1000));

        List<ProfitLineRow> lines = jdbcTemplate.query(
                "SELECT b.id AS bill_id, b.bill_number, b.billed_at,"
                        + " bl.item_sku, bl.item_name, bl.quantity,"
                        + " (bl.line_total - bl.tax_amount) AS revenue,"
                        + " s.serial_number, s.imei1,"
                        + " COALESCE(s.cost_price, bl.unit_cost * bl.quantity) AS cost"
                        + " FROM bill_lines bl JOIN bills b ON b.id = bl.bill_id"
                        + " LEFT JOIN bill_line_serials bls ON bls.bill_line_id = bl.id"
                        + " LEFT JOIN item_serials s ON s.id = bls.item_serial_id"
                        + " WHERE b.billed_at >= ? AND b.billed_at < ?"
                        + " AND b.status <> 'VOIDED'"
                        + " ORDER BY b.billed_at DESC LIMIT ?",
                (rs, rowNum) -> {
                    BigDecimal revenue = Money.of(rs.getBigDecimal("revenue"));
                    BigDecimal cost = Money.of(rs.getBigDecimal("cost"));
                    BigDecimal profit = Money.subtract(revenue, cost);
                    return new ProfitLineRow(
                            rs.getObject("bill_id", UUID.class),
                            rs.getString("bill_number"),
                            rs.getTimestamp("billed_at").toInstant(),
                            rs.getString("item_sku"),
                            rs.getString("item_name"),
                            rs.getString("serial_number"),
                            rs.getString("imei1"),
                            rs.getBigDecimal("quantity"),
                            revenue, cost, profit,
                            marginPercent(profit, revenue));
                },
                Timestamp.from(fromInstant), Timestamp.from(toInstant), capped);

        BigDecimal revenue = Money.ZERO;
        BigDecimal cost = Money.ZERO;
        for (ProfitLineRow row : lines) {
            revenue = Money.add(revenue, row.revenue());
            cost = Money.add(cost, row.cost());
        }
        BigDecimal profit = Money.subtract(revenue, cost);

        return new ProfitReportResponse(rangeFrom, rangeTo, revenue, cost, profit,
                marginPercent(profit, revenue), currency(), lines);
    }

    /** Stock on hand at cost: serialised units at their own, the rest at average. */
    @Transactional(readOnly = true)
    public StockValuationResponse stockValuation() {
        TenantContext.requireTenantId();

        List<StockValuationRow> rows = jdbcTemplate.query(
                "SELECT i.id, i.sku, i.name, i.has_serial_tracking,"
                        + " i.quantity_on_hand, i.cost_price,"
                        + " COALESCE(su.units, 0) AS serial_units,"
                        + " COALESCE(su.unit_value, 0) AS serial_value"
                        + " FROM items i LEFT JOIN ("
                        + "   SELECT item_id, COUNT(*) AS units,"
                        + "          SUM(COALESCE(cost_price, 0)) AS unit_value"
                        + "     FROM item_serials WHERE status = 'IN_STOCK' GROUP BY item_id"
                        + " ) su ON su.item_id = i.id"
                        + " WHERE i.deleted_at IS NULL AND i.track_inventory = true"
                        + " ORDER BY i.name",
                (rs, rowNum) -> {
                    boolean serialised = rs.getBoolean("has_serial_tracking");
                    BigDecimal qty = serialised
                            ? new BigDecimal(rs.getLong("serial_units"))
                            : Money.quantity(rs.getBigDecimal("quantity_on_hand"));
                    BigDecimal unitCost = Money.of(rs.getBigDecimal("cost_price"));
                    BigDecimal value = serialised
                            ? Money.of(rs.getBigDecimal("serial_value"))
                            : Money.of(qty.multiply(unitCost));
                    return new StockValuationRow(
                            rs.getObject("id", UUID.class),
                            rs.getString("sku"),
                            rs.getString("name"),
                            qty, unitCost, value, serialised);
                });

        BigDecimal serialised = Money.ZERO;
        BigDecimal quantity = Money.ZERO;
        for (StockValuationRow row : rows) {
            if (row.serialised()) {
                serialised = Money.add(serialised, row.value());
            } else {
                quantity = Money.add(quantity, row.value());
            }
        }

        // Damaged units are held aside from sellable stock but are still an asset
        // the shop owns, so they are reported separately rather than folded in.
        BigDecimal damaged = Money.of(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity_damaged * cost_price), 0) FROM items"
                        + " WHERE deleted_at IS NULL AND quantity_damaged > 0",
                BigDecimal.class));

        return new StockValuationResponse(
                Money.add(serialised, quantity), serialised, quantity, damaged,
                currency(), rows);
    }

    private static BigDecimal marginPercent(BigDecimal profit, BigDecimal revenue) {
        if (revenue == null || revenue.signum() == 0) {
            return Money.ZERO;
        }
        return profit.multiply(Money.HUNDRED)
                .divide(revenue, 2, java.math.RoundingMode.HALF_UP);
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
