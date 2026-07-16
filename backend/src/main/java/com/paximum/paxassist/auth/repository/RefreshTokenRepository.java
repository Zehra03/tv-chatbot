package com.paximum.paxassist.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.paximum.paxassist.auth.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every still-active refresh token belonging to the user (used by logout to kill all
     * sessions). A bulk update avoids loading the rows; callers run it inside a transaction.
     */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.user.id = :userId and t.revokedAt is null")
    int revokeAllActiveForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
