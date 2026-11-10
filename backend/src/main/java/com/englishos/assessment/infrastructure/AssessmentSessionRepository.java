package com.englishos.assessment.infrastructure;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.englishos.assessment.domain.AssessmentSession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentSessionRepository extends JpaRepository<AssessmentSession, Long> {

    /** Bloquea la sesión: dos respuestas simultáneas no pueden avanzar la escalera dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AssessmentSession s where s.publicId = :publicId")
    Optional<AssessmentSession> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<AssessmentSession> findByPublicId(String publicId);

    List<AssessmentSession> findByUserIdAndStatusOrderByIdDesc(Long userId, AssessmentSession.Status status);
}
