package com.englishos.wallet.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.englishos.wallet.domain.AccountCode;
import com.englishos.wallet.domain.LedgerAccount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, Long> {

    /** Crea la cuenta si no existe, sin carreras entre transacciones concurrentes. */
    @Modifying
    @Query(value = """
            insert ignore into ledger_accounts (code, owner_key, currency, balance, created_at, version)
            values (:code, :ownerKey, 'MXN', 0, :now, 0)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("code") String code, @Param("ownerKey") long ownerKey, @Param("now") Instant now);

    Optional<LedgerAccount> findByCodeAndOwnerKey(AccountCode code, long ownerKey);

    /**
     * Solo el id: no carga la entidad en el contexto de persistencia antes de bloquearla. Si se cargara aquí, el
     * bloqueo posterior reutilizaría esa copia (con saldo y versión viejos) y fallaría con conflictos optimistas.
     */
    @Query("select a.id from LedgerAccount a where a.code = :code and a.ownerKey = :ownerKey")
    Optional<Long> findIdByCodeAndOwnerKey(@Param("code") AccountCode code, @Param("ownerKey") long ownerKey);

    /** Bloqueo pesimista en orden de id: todas las transacciones bloquean en el mismo orden (sin interbloqueos). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LedgerAccount a where a.id in :ids order by a.id")
    List<LedgerAccount> lockAll(@Param("ids") Collection<Long> ids);

    List<LedgerAccount> findByOwnerKey(long ownerKey);

    List<LedgerAccount> findByOwnerKeyAndCodeIn(long ownerKey, Collection<AccountCode> codes);
}
