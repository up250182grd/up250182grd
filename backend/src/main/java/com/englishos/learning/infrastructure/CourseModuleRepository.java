package com.englishos.learning.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.englishos.learning.domain.CourseModule;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    Optional<CourseModule> findBySlug(String slug);

    Optional<CourseModule> findByPublicId(String publicId);

    List<CourseModule> findByCourseIdInOrderBySortOrderAsc(Collection<Long> courseIds);
}
