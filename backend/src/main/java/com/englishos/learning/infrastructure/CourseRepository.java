package com.englishos.learning.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.englishos.learning.domain.ContentStatus;
import com.englishos.learning.domain.Course;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findBySlug(String slug);

    Optional<Course> findByPublicId(String publicId);

    List<Course> findByStatusOrderByLevelAscSortOrderAsc(ContentStatus status);

    List<Course> findAllByOrderByLevelAscSortOrderAsc();

    List<Course> findByIdIn(Collection<Long> ids);
}
