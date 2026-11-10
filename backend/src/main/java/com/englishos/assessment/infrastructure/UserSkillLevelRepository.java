package com.englishos.assessment.infrastructure;

import java.util.List;

import com.englishos.assessment.domain.UserSkillLevel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillLevelRepository extends JpaRepository<UserSkillLevel, UserSkillLevel.Key> {

    @Query("select l from UserSkillLevel l where l.key.userId = :userId")
    List<UserSkillLevel> findByUserId(@Param("userId") Long userId);
}
