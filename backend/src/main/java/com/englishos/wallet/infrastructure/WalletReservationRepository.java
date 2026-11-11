package com.englishos.wallet.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.englishos.wallet.domain.WalletReservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletReservationRepository extends JpaRepository<WalletReservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WalletReservation r where r.subjectType = :type and r.subjectId = :id")
    Optional<WalletReservation> findForUpdate(@Param("type") String subjectType, @Param("id") Long subjectId);

    Optional<WalletReservation> findBySubjectTypeAndSubjectId(String subjectType, Long subjectId);
}
