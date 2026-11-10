package com.englishos.learning.infrastructure;

import java.time.Instant;
import java.util.List;

import com.englishos.learning.domain.LessonProgress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, LessonProgress.Key> {

    @Query("select p from LessonProgress p where p.key.userId = :userId")
    List<LessonProgress> findByUserId(@Param("userId") Long userId);

    /** Crea el registro si no existe, sin carreras entre dos "empezar" simultáneos. */
    @Modifying
    @Query(value = """
            insert ignore into lesson_progress (user_id, lesson_id, state, completed_runs, last_activity_at, version)
            values (:userId, :lessonId, 'IN_PROGRESS', 0, :now, 0)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("lessonId") Long lessonId, @Param("now") Instant now);
}
