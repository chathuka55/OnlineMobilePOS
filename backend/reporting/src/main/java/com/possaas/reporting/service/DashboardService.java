package com.possaas.reporting.service;

import com.possaas.common.jdbc.SqlArgs;
import com.possaas.common.tenant.TenantContext;
import com.possaas.reporting.api.dto.ReportingDtos.DashboardResponse;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantRepository tenantRepository;

    public DashboardService(JdbcTemplate jdbcTemplate, TenantRepository tenantRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantRepository = tenantRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse today() {
        UUID tenantId = TenantContext.requireTenantId();
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        ZoneId zone = ZoneId.of(tenant != null ? tenant.getTimeZone() : "Asia/Colombo");
        String currency = tenant != null ? tenant.getDefaultCurrency() : "LKR";
        LocalDate businessDate = LocalDate.now(zone);
        Instant from = businessDate.atStartOfDay(zone).toInstant();
        Instant to = businessDate.plusDays(1).atStartOfDay(zone).toInstant();

        BigDecimal todaySales = jdbcTemplate.queryForObject(
                """
                        SELECT COALESCE(SUM(grand_total), 0)
                          FROM bills
                         WHERE tenant_id = ?
                           AND billed_at >= ?
                           AND billed_at < ?
                           AND status <> 'VOIDED'
                        """,
                BigDecimal.class,
                SqlArgs.of(tenantId, from, to));
        Long billCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                          FROM bills
                         WHERE tenant_id = ?
                           AND billed_at >= ?
                           AND billed_at < ?
                           AND status <> 'VOIDED'
                        """,
                Long.class,
                SqlArgs.of(tenantId, from, to));
        Long lowStock = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                          FROM items
                         WHERE tenant_id = ?
                           AND deleted_at IS NULL
                           AND is_active = true
                           AND track_inventory = true
                           AND quantity_on_hand <= reorder_level
                        """,
                Long.class,
                SqlArgs.of(tenantId));
        Long openRepairs = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                          FROM repair_orders
                         WHERE tenant_id = ?
                           AND status NOT IN ('DELIVERED', 'CANCELLED')
                        """,
                Long.class,
                SqlArgs.of(tenantId));

        return new DashboardResponse(
                businessDate,
                todaySales == null ? BigDecimal.ZERO : todaySales,
                billCount == null ? 0L : billCount,
                lowStock == null ? 0L : lowStock,
                openRepairs == null ? 0L : openRepairs,
                currency);
    }
}
