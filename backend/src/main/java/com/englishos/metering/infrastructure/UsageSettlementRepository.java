package com.englishos.metering.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.englishos.metering.domain.UsageSettlement;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsageSettlementRepository extends JpaRepository<UsageSettlement, Long> {

    Optional<UsageSettlement> findBySubjectTypeAndSubjectId(String subjectType, Long subjectId);

    @Query("select s from UsageSettlement s where s.userId = :userId order by s.id desc")
    List<UsageSettlement> findRecentByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select coalesce(sum(s.totalMxn), 0) from UsageSettlement s
            where s.userId = :userId and s.status <> 'PENDING_RECONCILIATION' and s.settledAt >= :since
            """)
    BigDecimal totalSince(@Param("userId") Long userId, @Param("since") Instant since);

    /** Totales por rol en un periodo: [rol, n, costo directo, recargo, impuestos, total]. */
    @Query("""
            select s.roleCode, count(s), coalesce(sum(s.directCostMxn), 0), coalesce(sum(s.markupMxn), 0),
                   coalesce(sum(s.taxMxn), 0), coalesce(sum(s.totalMxn), 0)
            from UsageSettlement s
            where s.status <> 'PENDING_RECONCILIATION' and s.settledAt >= :from and s.settledAt < :to
            group by s.roleCode
            """)
    List<Object[]> totalsByRole(@Param("from") Instant from, @Param("to") Instant to);

    long countByStatus(UsageSettlement.Status status);
}
