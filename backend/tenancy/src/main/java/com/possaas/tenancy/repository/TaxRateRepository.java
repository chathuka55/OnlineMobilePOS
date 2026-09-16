package com.possaas.tenancy.repository;

import com.possaas.tenancy.domain.TaxRate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

    Optional<TaxRate> findByDefaultRateIsTrueAndActiveIsTrue();

    Optional<TaxRate> findByCodeIgnoreCase(String code);

    List<TaxRate> findByActiveIsTrueOrderByNameAsc();
}
