package com.possaas.sales.dto;

import com.possaas.sales.domain.CashMovementType;
import com.possaas.sales.domain.ShiftStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ShiftDtos {

    private ShiftDtos() {
    }

    public record OpenShiftRequest(
            UUID outletId,
            @DecimalMin("0") BigDecimal openingFloat,
            String note
    ) {
    }

    public record CashMovementRequest(
            @NotNull CashMovementType movementType,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 240) String reason,
            @Size(max = 120) String reference
    ) {
    }

    public record CloseShiftRequest(
            @NotNull @DecimalMin("0") BigDecimal countedCash,
            /** Note to denomination: {"5000": 4, "1000": 12}. Optional. */
            Map<String, Integer> denominations,
            String note
    ) {
    }

    public record CashMovementResponse(
            UUID id,
            CashMovementType movementType,
            BigDecimal amount,
            String reason,
            String reference,
            Instant occurredAt
    ) {
    }

    /**
     * The drawer reconciliation. Every component is shown rather than just the
     * total, because "we are short" is only actionable if you can see which part
     * of the arithmetic to go and check.
     */
    public record ShiftReport(
            UUID shiftId,
            String shiftNumber,
            ShiftStatus status,
            UUID outletId,
            Instant openedAt,
            Instant closedAt,
            BigDecimal openingFloat,
            BigDecimal cashSales,
            BigDecimal cashRepairs,
            BigDecimal cashWholesale,
            BigDecimal cashRefunds,
            BigDecimal payIns,
            BigDecimal payouts,
            BigDecimal drops,
            BigDecimal expectedCash,
            BigDecimal countedCash,
            BigDecimal variance,
            java.util.List<CashMovementResponse> movements
    ) {
    }
}
