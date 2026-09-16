package com.possaas.wholesale.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wholesale_invoice_line_serials")
@Getter
@Setter
@NoArgsConstructor
public class WholesaleInvoiceLineSerial extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wholesale_invoice_line_id", nullable = false)
    private WholesaleInvoiceLine invoiceLine;

    @Column(name = "item_serial_id", nullable = false)
    private UUID itemSerialId;

    @Column(name = "serial_number", nullable = false)
    private String serialNumber;
}
