package com.possaas.tenancy.domain;

import com.possaas.common.jpa.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A configurable tax rate. The desktop application had no tax handling at all, so this
 * is new: every sellable line can carry a rate, and documents summarise it.
 */
@Entity
@Table(name = "tax_rates")
@Getter
@Setter
@NoArgsConstructor
public class TaxRate extends TenantEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "rate_percent", nullable = false, precision = 9, scale = 4)
    private BigDecimal ratePercent = BigDecimal.ZERO;

    /**
     * True when shelf prices already contain the tax, which is the norm for Sri Lankan
     * retail. Inclusive rates are extracted from the line total rather than added to it.
     */
    @Column(name = "is_inclusive", nullable = false)
    private boolean inclusive;

    @Column(name = "is_default", nullable = false)
    private boolean defaultRate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.now();

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    public boolean isEffectiveOn(LocalDate date) {
        if (!active || date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !date.isAfter(effectiveTo);
    }
}
