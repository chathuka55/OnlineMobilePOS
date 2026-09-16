package com.possaas.sales.repository;

import com.possaas.sales.domain.Payment;
import com.possaas.sales.domain.PaymentDocumentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByDocumentTypeAndDocumentIdOrderByReceivedAtAsc(
            PaymentDocumentType documentType, UUID documentId);
}
