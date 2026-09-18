package com.possaas.tenancy.repository;

import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.domain.TenantStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Not tenant-scoped by RLS: login must resolve a slug to a tenant before any scope
 * exists, and the platform console needs to list every tenant.
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlugIgnoreCaseAndDeletedAtIsNull(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    Page<Tenant> findByDeletedAtIsNull(Pageable pageable);

    List<Tenant> findByStatusAndDeletedAtIsNull(TenantStatus status);

    @Query("""
            SELECT t FROM Tenant t
            WHERE t.deletedAt IS NULL
              AND (:status IS NULL OR t.status = :status)
              AND (:search IS NULL
                   OR LOWER(t.businessName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(t.slug) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(t.contactEmail) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """)
    Page<Tenant> search(@Param("search") String search,
                        @Param("status") TenantStatus status,
                        Pageable pageable);

    @Query("SELECT COUNT(t) FROM Tenant t WHERE t.status = :status AND t.deletedAt IS NULL")
    long countByStatus(@Param("status") TenantStatus status);
}
