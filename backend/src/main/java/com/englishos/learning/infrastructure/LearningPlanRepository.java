package com.englishos.learning.infrastructure;

import java.util.List;
import java.util.Optional;

import com.englishos.learning.domain.LearningPlan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearningPlanRepository extends JpaRepository<LearningPlan, Long> {

    @Query("select p from LearningPlan p where p.userId = :userId and p.active = true order by p.id desc")
    List<LearningPlan> findActive(@Param("userId") Long userId);

    default Optional<LearningPlan> findCurrent(Long userId) {
        return findActive(userId).stream().findFirst();
    }
}
