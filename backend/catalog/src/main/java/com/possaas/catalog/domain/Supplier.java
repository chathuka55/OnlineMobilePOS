package com.possaas.catalog.domain;

import com.possaas.common.jpa.TenantEntity;
import com.possaas.common.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "suppliers")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends TenantEntity {

    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "contact_person")
    private String contactPerson;

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

    @Column(name = "payment_terms_days", nullable = false)
    private short paymentTermsDays;

    @Column(name = "total_purchased", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPurchased = Money.ZERO;

    @Column(name = "outstanding_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstandingPayable = Money.ZERO;

    @Column(name = "notes")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.active = false;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
