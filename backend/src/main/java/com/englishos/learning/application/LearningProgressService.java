package com.englishos.learning.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.englishos.learning.api.LearningProgressApi;
import com.englishos.learning.domain.Lesson;
import com.englishos.learning.domain.LessonProgress;
import com.englishos.learning.infrastructure.ExerciseAttemptRepository;
import com.englishos.learning.infrastructure.LessonProgressRepository;
import com.englishos.learning.infrastructure.LessonRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

@Service
class LearningProgressService implements LearningProgressApi {

    private final LessonProgressRepository progress;
    private final LessonRepository lessons;
    private final ExerciseAttemptRepository attempts;
    private final CatalogService catalog;
    private final Json json;

    LearningProgressService(LessonProgressRepository progress, LessonRepository lessons,
            ExerciseAttemptRepository attempts, CatalogService catalog, Json json) {
        this.progress = progress;
        this.lessons = lessons;
        this.attempts = attempts;
        this.catalog = catalog;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LessonVocabulary> vocabularyOfPracticedLessons(Long userId) {
        List<Long> lessonIds = progress.findByUserId(userId).stream()
                .filter(p -> p.getCompletedRuns() > 0)
                .map(LessonProgress::getLessonId)
                .toList();
        if (lessonIds.isEmpty()) {
            return List.of();
        }
        List<LessonVocabulary> out = new ArrayList<>();
        for (Lesson l : lessons.findByIdIn(lessonIds)) {
            List<Term> terms = new ArrayList<>();
            JsonNode vocab = json.read(l.getVocabularyJson());
            for (JsonNode t : vocab) {
                String term = t.path("term").asString("").strip();
                String translation = t.path("translation").asString("").strip();
                if (term.isEmpty() || translation.isEmpty()) {
                    continue; // entrada incompleta: no se inventa la traducción
                }
                terms.add(new Term(term, translation, t.path("partOfSpeech").asString("other"),
                        t.path("ipa").isMissingNode() || t.path("ipa").isNull() ? null : t.path("ipa").asString()));
            }
            out.add(new LessonVocabulary(l.getPublicId(), l.getLevel(), terms));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NextLesson> nextLesson(Long userId) {
        var r = catalog.summary(userId).recommended();
        return r == null ? Optional.empty()
                : Optional.of(new NextLesson(r.publicId(), r.title(), r.level(), r.estimatedMinutes(), r.state()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Attempt> attemptsSince(Long userId, Instant from) {
        return attempts.findByUserIdAndCreatedAtGreaterThanEqual(userId, from).stream()
                .map(a -> new Attempt(a.getCreatedAt(), a.getDurationMs()))
                .toList();
    }
}
