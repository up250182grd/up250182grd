package com.englishos.learning.infrastructure;

import java.util.List;
import java.util.Optional;

import com.englishos.learning.domain.ExerciseAttempt;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseAttemptRepository extends JpaRepository<ExerciseAttempt, Long> {

    Optional<ExerciseAttempt> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    List<ExerciseAttempt> findByLessonRunIdOrderByIdAsc(Long lessonRunId);

    List<ExerciseAttempt> findByUserIdAndCreatedAtGreaterThanEqual(Long userId, java.time.Instant from);
}
