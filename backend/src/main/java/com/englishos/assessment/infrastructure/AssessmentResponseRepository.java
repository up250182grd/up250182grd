package com.englishos.assessment.infrastructure;

import java.util.Set;

import com.englishos.assessment.domain.AssessmentResponse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentResponseRepository extends JpaRepository<AssessmentResponse, Long> {

    @Query("select r.itemId from AssessmentResponse r where r.sessionId = :sessionId")
    Set<Long> answeredItemIds(@Param("sessionId") Long sessionId);
}
