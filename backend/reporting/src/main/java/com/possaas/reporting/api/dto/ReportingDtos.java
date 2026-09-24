package com.possaas.reporting.api.dto;

import com.possaas.common.audit.AuditSeverity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReportingDtos {

    private ReportingDtos() {
    }

    public record DashboardResponse(
            LocalDate businessDate,
            BigDecimal todaySales,
            long todayBillCount,
            long lowStockCount,
            long openRepairsCount,
            String currency
    ) {
    }

    public record DailySummaryResponse(
            LocalDate date,
            BigDecimal salesTotal,
            long billCount,
            BigDecimal refundTotal,
            long refundCount,
            BigDecimal netSales,
            String currency
    ) {
    }

    public record MonthlySummaryResponse(
            int year,
            int month,
            BigDecimal salesTotal,
            long billCount,
            BigDecimal refundTotal,
            long refundCount,
            BigDecimal netSales,
            List<DailySummaryResponse> days,
            String currency
    ) {
    }

    public record SalesRangeResponse(
            LocalDate from,
            LocalDate to,
            BigDecimal totalRevenue,
            long totalBills,
            BigDecimal averageBillValue,
            BigDecimal totalRefunds,
            List<SalesDayRow> dailyBreakdown,
            String currency
    ) {
    }

    public record SalesDayRow(
            LocalDate date,
            long billsCount,
            BigDecimal revenue,
            BigDecimal refunds,
            BigDecimal net
    ) {
    }

    public record TopCustomerRow(
            UUID id,
            String name,
            String type,
            BigDecimal totalPurchases,
            BigDecimal outstandingBalance,
            Instant lastPurchaseDate
    ) {
    }

    public record AuditEventResponse(
            UUID id,
            UUID tenantId,
            UUID outletId,
            String entityType,
            UUID entityId,
            String entityNumber,
            String action,
            AuditSeverity severity,
            UUID actorId,
            String actorEmail,
            String actorName,
            UUID impersonatorId,
            String summary,
            Map<String, Object> changes,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
    }

    public record RecordAuditRequest(
            String entityType,
            UUID entityId,
            String entityNumber,
            String action,
            AuditSeverity severity,
            String summary,
            Map<String, Object> changes,
            Map<String, Object> metadata
    ) {
    }

    // --- VAT output ---------------------------------------------------------

    /**
     * What a VAT return needs: the tax charged on sales in a period, and the
     * taxable value it was charged on. Figures are summed from the stored line
     * values rather than recomputed from rates, so the report always ties to the
     * bills that were actually printed.
     */
    public record VatOutputResponse(
            LocalDate from,
            LocalDate to,
            boolean vatRegistered,
            String vatTin,
            long billCount,
            BigDecimal grossSales,
            BigDecimal taxableValue,
            BigDecimal vatOutput,
            BigDecimal refundedGross,
            BigDecimal refundedVat,
            BigDecimal netVatPayable,
            String currency,
            List<VatRateRow> byRate
    ) {
    }

    public record VatRateRow(
            BigDecimal ratePercent,
            BigDecimal taxableValue,
            BigDecimal vatAmount
    ) {
    }

    // --- Profitability ------------------------------------------------------

    /**
     * Gross profit for one sold line. For a serialised unit the cost is that
     * unit's own purchase cost, which is the whole point of tracking IMEIs: two
     * identical handsets bought at different prices have different margins.
     */
    public record ProfitLineRow(
            UUID billId,
            String billNumber,
            Instant billedAt,
            String itemSku,
            String itemName,
            String serialNumber,
            String imei1,
            BigDecimal quantity,
            BigDecimal revenue,
            BigDecimal cost,
            BigDecimal grossProfit,
            BigDecimal marginPercent
    ) {
    }

    public record ProfitReportResponse(
            LocalDate from,
            LocalDate to,
            BigDecimal totalRevenue,
            BigDecimal totalCost,
            BigDecimal totalGrossProfit,
            BigDecimal marginPercent,
            String currency,
            List<ProfitLineRow> lines
    ) {
    }

    // --- Stock valuation ----------------------------------------------------

    public record StockValuationRow(
            UUID itemId,
            String sku,
            String itemName,
            BigDecimal quantityOnHand,
            BigDecimal unitCost,
            BigDecimal value,
            boolean serialised
    ) {
    }

    /**
     * Serialised units are valued at the sum of their own costs; everything else
     * at quantity times the item's weighted-average cost.
     */
    public record StockValuationResponse(
            BigDecimal totalValue,
            BigDecimal serialisedValue,
            BigDecimal quantityValue,
            BigDecimal damagedValue,
            String currency,
            List<StockValuationRow> items
    ) {
    }
}
