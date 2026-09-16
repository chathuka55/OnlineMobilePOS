package com.possaas.sales.domain;

import com.possaas.common.jpa.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends TenantOwnedEntity {

    @Column(name = "outlet_id")
    private UUID outletId;

    @Column(name = "payment_number")
    private String paymentNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private PaymentDocumentType documentType;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private PaymentDirection direction = PaymentDirection.IN;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency = "LKR";

    @Column(name = "tendered_amount", precision = 14, scale = 2)
    private BigDecimal tenderedAmount;

    @Column(name = "change_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal changeAmount = BigDecimal.ZERO;

    @Column(name = "reference")
    private String reference;

    @Column(name = "card_last4")
    private String cardLast4;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "cheque_number")
    private String chequeNumber;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;

    @Column(name = "cheque_status")
    private String chequeStatus;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversal_reason")
    private String reversalReason;

    @Column(name = "note")
    private String note;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
