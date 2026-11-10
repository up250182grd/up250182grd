package com.englishos.learning.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.englishos.learning.domain.Lesson;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    Optional<Lesson> findBySlug(String slug);

    Optional<Lesson> findByPublicId(String publicId);

    List<Lesson> findByModuleIdInOrderBySortOrderAsc(Collection<Long> moduleIds);

    List<Lesson> findByModuleIdOrderBySortOrderAsc(Long moduleId);

    List<Lesson> findByIdIn(Collection<Long> ids);

    @Query("select coalesce(max(l.sortOrder), 0) from Lesson l where l.moduleId = :moduleId")
    int maxSortOrder(@Param("moduleId") Long moduleId);
}
