package com.possaas.tenancy.repository;

import com.possaas.tenancy.domain.Outlet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads are constrained to the current tenant by Row-Level Security, so no query here
 * needs a {@code tenantId} parameter.
 */
public interface OutletRepository extends JpaRepository<Outlet, UUID> {

    Optional<Outlet> findByDefaultOutletIsTrue();

    Optional<Outlet> findByCodeIgnoreCase(String code);

    List<Outlet> findByActiveIsTrueOrderByNameAsc();

    long countByActiveIsTrue();
}
