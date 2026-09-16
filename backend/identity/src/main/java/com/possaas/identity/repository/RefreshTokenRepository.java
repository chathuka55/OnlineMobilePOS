package com.possaas.identity.repository;

import com.possaas.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdOrderByIssuedAtDesc(UUID userId);

    List<RefreshToken> findByFamilyId(UUID familyId);

    /**
     * Revokes an entire token family after a rotated token is replayed, which is the
     * signal that the value leaked.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now, t.revokedReason = :reason
             WHERE t.familyId = :familyId
               AND t.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId,
                     @Param("reason") String reason,
                     @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :now, t.revokedReason = :reason
             WHERE t.userId = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId,
                         @Param("reason") String reason,
                         @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
