package com.englishos.vocabulary.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.englishos.vocabulary.domain.VocabularyReview;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VocabularyReviewRepository extends JpaRepository<VocabularyReview, Long> {

    Optional<VocabularyReview> findByUserIdAndClientReviewId(Long userId, String clientReviewId);

    List<VocabularyReview> findByUserIdAndReviewedAtGreaterThanEqual(Long userId, Instant from);
}
