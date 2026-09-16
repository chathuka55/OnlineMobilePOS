package com.possaas.sales.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bill_line_serials")
@Getter
@Setter
@NoArgsConstructor
public class BillLineSerial extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_line_id", nullable = false)
    private BillLine billLine;

    @Column(name = "item_serial_id", nullable = false)
    private UUID itemSerialId;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;

    @Column(name = "returned_at")
    private Instant returnedAt;
}
