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
@Table(name = "items")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Item extends TenantEntity {

    @Column(name = "sku", nullable = false)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    @Column(name = "unit_of_measure", nullable = false)
    private String unitOfMeasure = "PCS";

    @Column(name = "image_object_key")
    private String imageObjectKey;

    @Column(name = "cost_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal costPrice = Money.ZERO;

    @Column(name = "retail_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal retailPrice = Money.ZERO;

    @Column(name = "wholesale_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal wholesalePrice = Money.ZERO;

    @Column(name = "min_selling_price", precision = 14, scale = 2)
    private BigDecimal minSellingPrice;

    @Column(name = "quantity_on_hand", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityOnHand = Money.quantity(BigDecimal.ZERO);

    @Column(name = "quantity_reserved", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityReserved = Money.quantity(BigDecimal.ZERO);

    /** Held aside, separate from quantity_on_hand - damaged stock is not sellable. */
    @Column(name = "quantity_damaged", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantityDamaged = Money.quantity(BigDecimal.ZERO);

    @Column(name = "reorder_level", nullable = false, precision = 14, scale = 3)
    private BigDecimal reorderLevel = Money.quantity(BigDecimal.ZERO);

    @Column(name = "reorder_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal reorderQuantity = Money.quantity(BigDecimal.ZERO);

    @Column(name = "track_inventory", nullable = false)
    private boolean trackInventory = true;

    @Column(name = "has_serial_tracking", nullable = false)
    private boolean hasSerialTracking;

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock;

    @Column(name = "is_old_stock", nullable = false)
    private boolean oldStock;

    @Column(name = "warranty_months", nullable = false)
    private short warrantyMonths;

    @Column(name = "warranty_label")
    private String warrantyLabel;

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

    public boolean isLowStock() {
        return trackInventory
                && active
                && deletedAt == null
                && quantityOnHand.compareTo(reorderLevel) <= 0;
    }

    public boolean isSellable() {
        return active && deletedAt == null;
    }

    public BigDecimal priceFor(boolean wholesale) {
        return wholesale ? wholesalePrice : retailPrice;
    }

    /** Applies a signed quantity delta to the denormalised on-hand cache. */
    public void applyQuantityDelta(BigDecimal delta) {
        this.quantityOnHand = Money.quantity(this.quantityOnHand.add(Money.quantity(delta)));
    }

    /** Applies a signed quantity delta to the damaged-stock bucket. */
    public void applyDamagedDelta(BigDecimal delta) {
        this.quantityDamaged = Money.quantity(this.quantityDamaged.add(Money.quantity(delta)));
    }
}
