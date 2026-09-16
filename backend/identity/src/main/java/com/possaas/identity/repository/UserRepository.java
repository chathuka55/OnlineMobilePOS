package com.possaas.identity.repository;

import com.possaas.identity.domain.User;
import com.possaas.identity.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Scoped by the RLS policy on {@code users}, which matches {@code tenant_id} against the
 * bound tenant — including the null-equals-null case that makes platform accounts visible
 * only when no tenant is bound.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByPlatformAdminTrue();

    long countByStatusAndDeletedAtIsNull(UserStatus status);

    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND (:status IS NULL OR u.status = :status)
              AND (:search IS NULL
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY u.fullName ASC
            """)
    Page<User> search(@Param("search") String search,
                      @Param("status") UserStatus status,
                      Pageable pageable);

    @Query("""
            SELECT u FROM User u
             JOIN u.roles r
             WHERE u.tenantId = :tenantId
               AND u.deletedAt IS NULL
               AND u.status = com.possaas.identity.domain.UserStatus.ACTIVE
               AND r.code = 'OWNER'
             ORDER BY u.createdAt ASC
            """)
    List<User> findActiveOwners(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT u FROM User u
             WHERE u.tenantId = :tenantId
               AND u.deletedAt IS NULL
               AND u.status = com.possaas.identity.domain.UserStatus.ACTIVE
             ORDER BY u.createdAt ASC
            """)
    List<User> findActiveByTenantId(@Param("tenantId") UUID tenantId);
}
