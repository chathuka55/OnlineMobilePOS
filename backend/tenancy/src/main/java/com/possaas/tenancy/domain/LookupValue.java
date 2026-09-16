package com.possaas.tenancy.domain;

import com.possaas.common.jpa.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A user-editable dropdown entry.
 *
 * <p>The desktop app appended these to flat files ({@code repair_types.txt},
 * {@code repair_conditions.txt}, {@code borrowed_items.txt}), which meant they could not
 * be shared between terminals or backed up with the data.
 */
@Entity
@Table(name = "lookup_values")
@Getter
@Setter
@NoArgsConstructor
public class LookupValue extends TenantEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "lookup_type", nullable = false)
    private LookupType lookupType;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public static LookupValue of(LookupType type, String value, int order) {
        LookupValue lookup = new LookupValue();
        lookup.lookupType = type;
        lookup.value = value;
        lookup.displayOrder = order;
        return lookup;
    }

    public enum LookupType {
        REPAIR_TYPE,
        DEVICE_CONDITION,
        BORROWED_ITEM,
        ITEM_CATEGORY,
        PAYMENT_NOTE,
        REFUND_REASON,
        RETURN_REASON,
        BANK
    }
}
