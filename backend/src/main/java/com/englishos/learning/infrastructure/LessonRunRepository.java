package com.englishos.learning.infrastructure;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.englishos.learning.domain.LessonRun;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRunRepository extends JpaRepository<LessonRun, Long> {

    Optional<LessonRun> findByPublicId(String publicId);

    /** Bloqueo de la ejecución al terminarla: dos "terminar" simultáneos no la cuentan dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LessonRun r where r.publicId = :publicId")
    Optional<LessonRun> findByPublicIdForUpdate(@Param("publicId") String publicId);

    List<LessonRun> findByUserIdAndLessonIdAndCompletedAtIsNotNullOrderByIdAsc(Long userId, Long lessonId);
}
