package com.possaas.subscription.repository;

import com.possaas.subscription.domain.Plan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findByCodeIgnoreCaseAndActiveIsTrue(String code);

    Optional<Plan> findByCodeIgnoreCase(String code);

    List<Plan> findByPublicPlanIsTrueAndActiveIsTrueOrderByDisplayOrderAsc();

    List<Plan> findByActiveIsTrueOrderByDisplayOrderAsc();
}
