package com.englishos.vocabulary.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.englishos.vocabulary.domain.VocabularyItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VocabularyItemRepository extends JpaRepository<VocabularyItem, Long> {

    Optional<VocabularyItem> findByTermAndPartOfSpeech(String term, String partOfSpeech);

    List<VocabularyItem> findByIdIn(Collection<Long> ids);

    /** Alta idempotente y sin carreras entre dos sincronizaciones simultáneas. */
    @Modifying
    @Query(value = """
            insert ignore into vocabulary_items
                (term, part_of_speech, ipa, translation_es, cefr_level, source_lesson, created_at, updated_at)
            values (:term, :pos, :ipa, :translation, :level, :lesson, :now, :now)""", nativeQuery = true)
    int insertIfAbsent(@Param("term") String term, @Param("pos") String partOfSpeech, @Param("ipa") String ipa,
            @Param("translation") String translation, @Param("level") String level, @Param("lesson") String lesson,
            @Param("now") Instant now);
}
