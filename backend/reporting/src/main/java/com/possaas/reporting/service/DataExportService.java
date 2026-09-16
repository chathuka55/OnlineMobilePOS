package com.possaas.reporting.service;

import com.possaas.common.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A full backup of a tenant's own data, as JSON: every business table, tenant-scoped,
 * dumped through {@link JdbcTemplate#queryForList} so it needs no per-entity mapping
 * and stays correct as columns are added elsewhere. Plain JDBC also means this can
 * run as one straight SELECT per table instead of hydrating JPA entity graphs.
 *
 * <p>Not a restore path - there's nowhere to re-import this yet - but it satisfies
 * "let me download my own data" without needing an object-storage provider or a
 * background job runner, neither of which exists in this deployment.
 */
@Service
public class DataExportService {

    /** Every tenant-owned business table worth backing up, in a safe dependency order. */
    private static final List<String> TABLES = List.of(
            "outlets", "tax_rates", "lookup_values",
            "customers", "customer_notes",
            "categories", "suppliers", "items", "item_barcodes", "item_serials",
            "goods_received_notes", "grn_lines", "supplier_returns", "supplier_return_lines",
            "carts", "cart_lines", "bills", "bill_lines", "bill_line_serials",
            "payments", "credit_notes", "credit_note_redemptions", "refunds", "refund_lines",
            "repair_orders", "repair_order_lines", "repair_status_history",
            "wholesale_invoices", "wholesale_invoice_lines", "customer_credit_ledger",
            "quotations", "quotation_lines"
    );

    private final JdbcTemplate jdbcTemplate;

    public DataExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportAll() {
        UUID tenantId = TenantContext.requireTenantId();
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("exportedAt", java.time.Instant.now().toString());
        bundle.put("tenantId", tenantId.toString());

        Map<String, List<Map<String, Object>>> tables = new LinkedHashMap<>();
        for (String table : TABLES) {
            // Safe to concatenate: table comes from the fixed TABLES constant above,
            // never from request input.
            tables.put(table, jdbcTemplate.queryForList(
                    "SELECT * FROM " + table + " WHERE tenant_id = ?", tenantId));
        }
        bundle.put("tables", tables);
        return bundle;
    }
}
