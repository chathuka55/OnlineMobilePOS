package com.possaas.subscription.repository;

import com.possaas.subscription.domain.SubscriptionInvoice;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionInvoiceRepository extends JpaRepository<SubscriptionInvoice, UUID> {

    List<SubscriptionInvoice> findByTenantIdOrderByIssuedAtDesc(UUID tenantId);
}
