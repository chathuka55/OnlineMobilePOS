package com.possaas.identity.repository;

import com.possaas.identity.domain.OneTimeToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    Optional<OneTimeToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE OneTimeToken t SET t.consumedAt = :now
             WHERE t.userId = :userId AND t.purpose = :purpose AND t.consumedAt IS NULL
            """)
    int invalidateOutstanding(@Param("userId") UUID userId,
                              @Param("purpose") OneTimeToken.Purpose purpose,
                              @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM OneTimeToken t WHERE t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
