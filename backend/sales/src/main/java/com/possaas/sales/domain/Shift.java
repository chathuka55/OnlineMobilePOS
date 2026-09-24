package com.possaas.sales.domain;

import com.possaas.common.jpa.TenantEntity;
import com.possaas.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shifts")
@Getter
@Setter
@NoArgsConstructor
public class Shift extends TenantEntity {

    @Column(name = "outlet_id", nullable = false)
    private UUID outletId;

    @Column(name = "shift_number", nullable = false)
    private String shiftNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShiftStatus status = ShiftStatus.OPEN;

    @Column(name = "opened_by")
    private UUID openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "opening_float", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingFloat = Money.ZERO;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "expected_cash", precision = 14, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "counted_cash", precision = 14, scale = 2)
    private BigDecimal countedCash;

    /** Counted minus expected. Negative is short, positive is over. */
    @Column(name = "variance", precision = 14, scale = 2)
    private BigDecimal variance;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "denominations")
    private String denominations;

    @Column(name = "note")
    private String note;

    public boolean isOpen() {
        return status == ShiftStatus.OPEN;
    }

    /**
     * Freezes the shift. The reconciliation is stored rather than recomputed on
     * read so a historic Z-report keeps saying what it said on the day.
     */
    public void close(BigDecimal expected, BigDecimal counted, UUID userId) {
        this.expectedCash = expected;
        this.countedCash = counted;
        this.variance = Money.subtract(counted, expected);
        this.closedAt = Instant.now();
        this.closedBy = userId;
        this.status = ShiftStatus.CLOSED;
    }
}
