package com.englishos.vocabulary.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import com.englishos.vocabulary.domain.VocabularyCard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VocabularyCardRepository extends JpaRepository<VocabularyCard, Long> {

    List<VocabularyCard> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from VocabularyCard c where c.publicId = :publicId")
    Optional<VocabularyCard> findByPublicIdForUpdate(@Param("publicId") String publicId);

    Optional<VocabularyCard> findByPublicId(String publicId);

    /** Tarjetas nuevas presentadas por primera vez desde {@code from} (cupo diario de nuevas). */
    long countByUserIdAndFirstReviewedAtGreaterThanEqual(Long userId, Instant from);

    @Modifying
    @Query(value = """
            insert ignore into user_vocabulary_cards
                (public_id, user_id, item_id, algorithm, algorithm_version, ease_factor, repetitions, interval_days,
                 learning_state, due_at, lapses, review_count, created_at, version)
            values (:publicId, :userId, :itemId, :algorithm, :version, 2.500, 0, 0, 'NEW', :now, 0, 0, :now, 0)""",
            nativeQuery = true)
    int insertIfAbsent(@Param("publicId") String publicId, @Param("userId") Long userId, @Param("itemId") Long itemId,
            @Param("algorithm") String algorithm, @Param("version") int algorithmVersion, @Param("now") Instant now);
}
