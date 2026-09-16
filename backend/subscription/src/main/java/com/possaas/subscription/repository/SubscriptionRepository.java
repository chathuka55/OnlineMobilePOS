package com.possaas.subscription.repository;

import com.possaas.subscription.domain.Subscription;
import com.possaas.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);

    List<Subscription> findByStatus(SubscriptionStatus status);

    @Query("""
            SELECT s FROM Subscription s
             WHERE s.status = com.possaas.subscription.domain.SubscriptionStatus.TRIALING
               AND s.trialEndsAt IS NOT NULL
               AND s.trialEndsAt <= :now
            """)
    List<Subscription> findExpiredTrials(@Param("now") Instant now);

    @Query("""
            SELECT s FROM Subscription s
             WHERE s.status = com.possaas.subscription.domain.SubscriptionStatus.ACTIVE
               AND s.currentPeriodEnd IS NOT NULL
               AND s.currentPeriodEnd <= :now
            """)
    List<Subscription> findLapsedActive(@Param("now") Instant now);

    @Query("""
            SELECT s FROM Subscription s
             WHERE s.status = com.possaas.subscription.domain.SubscriptionStatus.PAST_DUE
            """)
    List<Subscription> findPastDue();

    @Query("""
            SELECT s FROM Subscription s
             WHERE s.status = com.possaas.subscription.domain.SubscriptionStatus.GRACE
               AND s.gracePeriodEndsAt IS NOT NULL
               AND s.gracePeriodEndsAt <= :now
            """)
    List<Subscription> findExpiredGrace(@Param("now") Instant now);

    long countByStatus(SubscriptionStatus status);
}
