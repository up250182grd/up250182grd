package com.englishos.identity.infrastructure;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.englishos.identity.domain.AuthToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    /** Bloqueo pesimista: dos usos simultáneos del mismo token no pueden tener éxito ambos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AuthToken t where t.tokenHash = :hash")
    Optional<AuthToken> findByHashForUpdate(@Param("hash") String hash);

    /** Invalida los tokens pendientes de un usuario para un propósito (al emitir uno nuevo o usar uno). */
    @Modifying
    @Query("""
            update AuthToken t set t.usedAt = :now
            where t.userId = :userId and t.purpose = :purpose and t.usedAt is null
            """)
    int invalidatePending(@Param("userId") Long userId, @Param("purpose") AuthToken.Purpose purpose,
            @Param("now") Instant now);
}
