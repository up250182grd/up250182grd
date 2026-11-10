package com.englishos.learning.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.Skill;
import com.englishos.learning.domain.ContentStatus;
import com.englishos.learning.domain.Exercise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    Optional<Exercise> findBySlug(String slug);

    Optional<Exercise> findByPublicId(String publicId);

    List<Exercise> findByLessonIdOrderBySortOrderAsc(Long lessonId);

    List<Exercise> findByLessonIdAndStatusOrderBySortOrderAsc(Long lessonId, ContentStatus status);

    List<Exercise> findByPurposeAndSkillAndLevelAndStatus(Exercise.Purpose purpose, Skill skill, CefrLevel level,
            ContentStatus status);

    List<Exercise> findByPurposeAndStatus(Exercise.Purpose purpose, ContentStatus status);

    @Query("select e.lessonId, count(e) from Exercise e where e.lessonId in :ids group by e.lessonId")
    List<Object[]> countByLessonIds(@Param("ids") Collection<Long> lessonIds);

    @Query("select coalesce(max(e.sortOrder), 0) from Exercise e where e.lessonId = :lessonId")
    int maxSortOrder(@Param("lessonId") Long lessonId);
}
