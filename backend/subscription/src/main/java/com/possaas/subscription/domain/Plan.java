package com.possaas.subscription.domain;

import com.possaas.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Platform-owned catalogue row. Not tenant-scoped and not under RLS.
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
public class Plan extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LKR";

    @Column(name = "price_monthly", nullable = false)
    private BigDecimal priceMonthly = BigDecimal.ZERO;

    @Column(name = "price_yearly", nullable = false)
    private BigDecimal priceYearly = BigDecimal.ZERO;

    @Column(name = "trial_days", nullable = false)
    private short trialDays = 14;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_outlets")
    private Integer maxOutlets;

    @Column(name = "max_items")
    private Integer maxItems;

    @Column(name = "max_monthly_bills")
    private Integer maxMonthlyBills;

    @Column(name = "is_public", nullable = false)
    private boolean publicPlan = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
