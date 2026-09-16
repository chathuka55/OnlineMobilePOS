package com.possaas.subscription.repository;

import com.possaas.subscription.domain.UsageCounter;
import com.possaas.subscription.domain.UsageMetric;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageCounterRepository extends JpaRepository<UsageCounter, UUID> {

    Optional<UsageCounter> findByTenantIdAndMetricAndPeriodKey(
            UUID tenantId, UsageMetric metric, String periodKey);
}
