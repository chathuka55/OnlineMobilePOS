package com.possaas.wholesale.repository;

import com.possaas.wholesale.domain.CustomerCreditLedger;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCreditLedgerRepository extends JpaRepository<CustomerCreditLedger, UUID> {

    Page<CustomerCreditLedger> findByCustomerIdOrderByOccurredAtDesc(UUID customerId, Pageable pageable);

    List<CustomerCreditLedger> findByDocumentTypeAndDocumentIdOrderByOccurredAtAsc(
            String documentType, UUID documentId);
}
