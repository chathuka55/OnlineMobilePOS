package com.possaas.subscription.repository;

import com.possaas.subscription.domain.GatewayEvent;
import com.possaas.subscription.domain.PaymentGatewayType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayEventRepository extends JpaRepository<GatewayEvent, UUID> {

    Optional<GatewayEvent> findByGatewayAndExternalEventId(
            PaymentGatewayType gateway, String externalEventId);

    boolean existsByGatewayAndExternalEventId(PaymentGatewayType gateway, String externalEventId);
}
