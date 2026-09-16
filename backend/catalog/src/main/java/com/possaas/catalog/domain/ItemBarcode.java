package com.possaas.catalog.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "item_barcodes")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ItemBarcode extends TenantOwnedEntity {

    @Column(name = "item_id", nullable = false, updatable = false)
    private UUID itemId;

    @Column(name = "barcode", nullable = false)
    private String barcode;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryBarcode;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ItemBarcode of(UUID itemId, String barcode, boolean primary) {
        ItemBarcode row = new ItemBarcode();
        row.itemId = itemId;
        row.barcode = barcode;
        row.primaryBarcode = primary;
        return row;
    }
}
