package com.englishos.wallet.infrastructure;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import com.englishos.wallet.domain.LedgerEntry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByTransactionIdIn(Collection<Long> transactionIds);

    /** Saldo recalculado desde las líneas, para verificar la caché (RN-18). */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.accountId = :accountId")
    BigDecimal sumByAccount(@Param("accountId") Long accountId);

    /** Invariante global: la suma de todas las líneas del libro es cero. */
    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e")
    BigDecimal sumAll();
}
