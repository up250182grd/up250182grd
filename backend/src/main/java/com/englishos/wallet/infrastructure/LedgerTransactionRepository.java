package com.englishos.wallet.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.englishos.wallet.domain.LedgerTransaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);

    List<LedgerTransaction> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(Instant from, Instant to);

    @Query("select t from LedgerTransaction t where t.userId = :userId order by t.id desc")
    Page<LedgerTransaction> findByUser(@Param("userId") Long userId, Pageable pageable);

    /** Lo efectivamente cobrado al usuario (capturas) desde {@code since}: base del límite mensual (RN-26). */
    @Query(value = """
            select coalesce(-sum(e.amount), 0)
            from ledger_entries e
            join ledger_transactions t on t.id = e.transaction_id
            join ledger_accounts a on a.id = e.account_id
            where t.type = 'CAPTURE' and t.user_id = :userId and t.created_at >= :since
              and a.owner_key = :userId and a.code in ('USER_AVAILABLE', 'USER_RESERVED')
            """, nativeQuery = true)
    BigDecimal capturedSince(@Param("userId") Long userId, @Param("since") Instant since);
}
