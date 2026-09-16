package com.possaas.crm.domain;

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

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends TenantEntity {

    @Column(name = "code")
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false)
    private CustomerType customerType = CustomerType.RETAIL;

    @Column(name = "phone_primary")
    private String phonePrimary;

    @Column(name = "phone_secondary")
    private String phoneSecondary;

    @Column(name = "email")
    private String email;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city")
    private String city;

    @Column(name = "tax_identifier")
    private String taxIdentifier;

    @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "outstanding_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstandingAmount = BigDecimal.ZERO;

    @Column(name = "lifetime_sales", nullable = false, precision = 14, scale = 2)
    private BigDecimal lifetimeSales = BigDecimal.ZERO;

    @Column(name = "loyalty_points", nullable = false)
    private int loyaltyPoints;

    @Column(name = "default_tax_rate_id")
    private UUID defaultTaxRateId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
    }

    public void updateCreditLimit(BigDecimal newLimit) {
        BigDecimal limit = Money.of(newLimit);
        if (limit.signum() < 0) {
            throw new IllegalArgumentException("Credit limit cannot be negative");
        }
        this.creditLimit = limit;
    }

    /** Positive delta increases what the customer owes. */
    public void adjustOutstanding(BigDecimal delta) {
        this.outstandingAmount = Money.add(this.outstandingAmount, delta);
    }
}
