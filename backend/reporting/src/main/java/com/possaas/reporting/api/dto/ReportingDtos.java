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
}
