package com.englishos.tutor.infrastructure;

import java.util.List;

import com.englishos.tutor.domain.LearnerError;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearnerErrorRepository extends JpaRepository<LearnerError, Long> {

    List<LearnerError> findByConversationIdOrderByIdAsc(Long conversationId);

    @Query("select e from LearnerError e where e.userId = :userId order by e.id desc")
    List<LearnerError> findRecentByUser(@Param("userId") Long userId, Pageable pageable);
}
