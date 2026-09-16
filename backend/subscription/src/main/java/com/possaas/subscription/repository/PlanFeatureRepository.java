package com.possaas.subscription.repository;

import com.possaas.subscription.domain.FeatureCode;
import com.possaas.subscription.domain.PlanFeature;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {

    List<PlanFeature> findByPlanId(UUID planId);

    Optional<PlanFeature> findByPlanIdAndFeatureCode(UUID planId, FeatureCode featureCode);

    boolean existsByPlanIdAndFeatureCodeAndEnabledIsTrue(UUID planId, FeatureCode featureCode);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM PlanFeature pf WHERE pf.planId = :planId")
    void deleteByPlanId(@Param("planId") UUID planId);
}
