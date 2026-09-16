package com.possaas.identity.repository;

import com.possaas.identity.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    /** System roles have no tenant and are shared by every workspace. */
    Optional<Role> findByCodeAndTenantIdIsNull(String code);

    Optional<Role> findByCodeAndTenantId(String code, UUID tenantId);

    /**
     * Everything assignable in the current workspace: the seeded system roles plus any
     * custom ones. RLS already limits the tenant-owned rows to the bound tenant.
     */
    @Query("SELECT r FROM Role r ORDER BY r.system DESC, r.name ASC")
    List<Role> findAllVisible();

    @Query("""
            SELECT r FROM Role r
            WHERE r.code = :code
              AND (r.tenantId IS NULL OR r.tenantId = :tenantId)
            ORDER BY r.tenantId NULLS LAST
            """)
    List<Role> findByCodePreferringTenant(@Param("code") String code,
                                          @Param("tenantId") UUID tenantId);
}
