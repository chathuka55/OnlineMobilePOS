package com.possaas.sales.domain;

import com.possaas.common.jpa.TenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
public class Cart extends TenantEntity {

    @Column(name = "outlet_id", nullable = false)
    private UUID outletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CartStatus status = CartStatus.DRAFT;

    @Column(name = "label")
    private String label;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name")
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false)
    private PriceMode priceMode = PriceMode.RETAIL;

    @Column(name = "note")
    private String note;

    @Column(name = "held_at")
    private Instant heldAt;

    @Column(name = "converted_bill_id")
    private UUID convertedBillId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNumber ASC")
    private List<CartLine> lines = new ArrayList<>();

    public void addLine(CartLine line) {
        line.setCart(this);
        lines.add(line);
    }

    public void removeLine(CartLine line) {
        lines.remove(line);
        line.setCart(null);
    }

    public boolean isEditable() {
        return status == CartStatus.DRAFT || status == CartStatus.HELD;
    }
}
