package com.englishos.vocabulary.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.englishos.identity.api.LearnerProfiles;
import com.englishos.learning.api.LearningProgressApi;
import com.englishos.shared.error.ApiException;
import com.englishos.shared.ids.Ulid;
import com.englishos.vocabulary.api.VocabularyApi;
import com.englishos.vocabulary.domain.ReviewQueue;
import com.englishos.vocabulary.domain.Sm2;
import com.englishos.vocabulary.domain.VocabularyCard;
import com.englishos.vocabulary.domain.VocabularyCard.LearningState;
import com.englishos.vocabulary.domain.VocabularyItem;
import com.englishos.vocabulary.domain.VocabularyReview;
import com.englishos.vocabulary.infrastructure.VocabularyCardRepository;
import com.englishos.vocabulary.infrastructure.VocabularyItemRepository;
import com.englishos.vocabulary.infrastructure.VocabularyReviewRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vocabulario con repetición espaciada (RF-SRS-1..4). Las tarjetas salen del vocabulario de las lecciones que el
 * alumno ya practicó; la próxima fecha la calcula SIEMPRE el servidor con SM-2 (el cliente solo envía la calidad).
 */
@Service
public class VocabularyService implements VocabularyApi {

    /** Botones de la interfaz → calidad SM-2. */
    public static final int[] GRADES = {1, 3, 4, 5};

    private final VocabularyItemRepository items;
    private final VocabularyCardRepository cards;
    private final VocabularyReviewRepository reviews;
    private final LearningProgressApi learning;
    private final LearnerProfiles profiles;
    private final Clock clock;
    private final int newPerDay;
    private final int maxQueue;
    private final org.springframework.context.ApplicationEventPublisher events;

    VocabularyService(VocabularyItemRepository items, VocabularyCardRepository cards,
            VocabularyReviewRepository reviews, LearningProgressApi learning, LearnerProfiles profiles, Clock clock,
            org.springframework.context.ApplicationEventPublisher events,
            @Value("${app.vocabulary.new-cards-per-day:10}") int newPerDay,
            @Value("${app.vocabulary.max-queue:50}") int maxQueue) {
        this.items = items;
        this.cards = cards;
        this.reviews = reviews;
        this.learning = learning;
        this.profiles = profiles;
        this.clock = clock;
        this.newPerDay = newPerDay;
        this.maxQueue = maxQueue;
        this.events = events;
    }

    // ---- Vistas -------------------------------------------------------------------------------

    public record VocabularyCardView(String id, String term, String partOfSpeech, String ipa, String translation, String level,
            String state, int repetitions, int intervalDays, BigDecimal easeFactor, int lapses, Instant dueAt,
            Instant lastReviewedAt) {
    }

    /** Intervalo (días) que resultaría de cada botón; lo calcula el servidor para mostrarlo antes de calificar. */
    public record VocabularyGradePreview(int quality, int intervalDays) {
    }

    public record VocabularyQueueItem(VocabularyCardView card, List<VocabularyGradePreview> previews) {
    }

    public record VocabularyQueue(List<VocabularyQueueItem> items, int dueCount, int newCount, int newAvailableToday, VocabularyStats stats) {
    }

    public record VocabularyReviewResult(VocabularyCardView card, int quality, boolean replayed) {
    }

    // ---- Casos de uso -------------------------------------------------------------------------

    @Override
    @Transactional
    public void sync(Long userId) {
        Instant now = Instant.now(clock);
        for (LearningProgressApi.LessonVocabulary lesson : learning.vocabularyOfPracticedLessons(userId)) {
            for (LearningProgressApi.Term t : lesson.terms()) {
                String term = clip(t.term(), 120);
                String pos = clip(t.partOfSpeech(), 24);
                String ipa = t.ipa() == null ? null : clip(t.ipa(), 120);
                String translation = clip(t.translation(), 255);
                items.insertIfAbsent(term, pos, ipa, translation, lesson.level().name(), lesson.lessonPublicId(), now);
                VocabularyItem item = items.findByTermAndPartOfSpeech(term, pos).orElseThrow();
                item.refresh(ipa, translation, now);
                cards.insertIfAbsent(Ulid.next(), userId, item.getId(), Sm2.ALGORITHM, Sm2.VERSION, now);
            }
        }
    }

    @Transactional
    public VocabularyQueue queue(Long userId) {
        sync(userId);
        Instant now = Instant.now(clock);
        List<VocabularyCard> all = cards.findByUserId(userId);
        int allowed = newAllowedToday(userId, now);
        List<VocabularyCard> queue = ReviewQueue.build(all, now, allowed, maxQueue);
        Map<Long, VocabularyItem> byId = itemsOf(all);
        List<VocabularyQueueItem> out = queue.stream()
                .map(c -> new VocabularyQueueItem(view(c, byId.get(c.getItemId())), previews(c)))
                .toList();
        int due = (int) queue.stream().filter(c -> c.getLearningState() != LearningState.NEW).count();
        return new VocabularyQueue(out, due, out.size() - due, allowed, stats(all, now, allowed));
    }

    @Transactional
    public VocabularyReviewResult review(Long userId, String cardId, int quality, String clientReviewId, Integer durationMs) {
        if (quality < 0 || quality > 5) {
            throw ApiException.badRequest("INVALID_QUALITY", "La calificación debe estar entre 0 y 5.");
        }
        if (clientReviewId == null || clientReviewId.isBlank() || clientReviewId.length() > 64) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Falta un identificador de repaso válido.");
        }
        VocabularyCard card = cards.findByPublicIdForUpdate(cardId)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("CARD_NOT_FOUND", "Tarjeta no encontrada."));
        VocabularyItem item = items.findById(card.getItemId()).orElseThrow();
        // Reintento del mismo repaso (doble clic, red inestable): no se vuelve a aplicar SM-2.
        var previous = reviews.findByUserIdAndClientReviewId(userId, clientReviewId);
        if (previous.isPresent()) {
            if (!previous.get().getCardId().equals(card.getId())) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Ese identificador ya se usó en otra tarjeta.");
            }
            return new VocabularyReviewResult(view(card, item), previous.get().getQuality(), true);
        }
        Instant now = Instant.now(clock);
        boolean wasLearned = card.getLearningState() == LearningState.REVIEW || card.isMature();
        Sm2.State before = card.state();
        Sm2.State after = card.review(quality, now, profiles.zoneOf(userId));
        Integer duration = durationMs == null ? null : Math.max(0, Math.min(durationMs, 600_000));
        reviews.save(new VocabularyReview(card.getId(), userId, clientReviewId, quality, json(before), json(after),
                duration, now));
        events.publishEvent(new com.englishos.vocabulary.api.VocabularyEvents.CardReviewed(userId, clientReviewId,
                quality, duration, !wasLearned && card.getLearningState() == LearningState.REVIEW
                        && card.getLapses() == 0, now));
        return new VocabularyReviewResult(view(card, item), quality, false);
    }

    @Transactional
    public List<VocabularyCardView> words(Long userId) {
        sync(userId);
        List<VocabularyCard> all = cards.findByUserId(userId);
        Map<Long, VocabularyItem> byId = itemsOf(all);
        return all.stream()
                .map(c -> view(c, byId.get(c.getItemId())))
                .sorted(Comparator.comparing(v -> v.term().toLowerCase(java.util.Locale.ROOT)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VocabularyStats stats(Long userId) {
        Instant now = Instant.now(clock);
        return stats(cards.findByUserId(userId), now, newAllowedToday(userId, now));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewEvent> reviewsSince(Long userId, Instant from) {
        return reviews.findByUserIdAndReviewedAtGreaterThanEqual(userId, from).stream()
                .map(r -> new ReviewEvent(r.getReviewedAt(), r.getDurationMs()))
                .toList();
    }

    // ---- Apoyo --------------------------------------------------------------------------------

    private VocabularyStats stats(List<VocabularyCard> all, Instant now, int newAllowed) {
        Map<LearningState, Long> byState = all.stream()
                .collect(Collectors.groupingBy(VocabularyCard::getLearningState, Collectors.counting()));
        int newCards = byState.getOrDefault(LearningState.NEW, 0L).intValue();
        int dueNow = (int) all.stream()
                .filter(c -> c.getLearningState() != LearningState.NEW && !c.getDueAt().isAfter(now)).count();
        return new VocabularyStats(all.size(), newCards, byState.getOrDefault(LearningState.LEARNING, 0L).intValue(),
                byState.getOrDefault(LearningState.REVIEW, 0L).intValue(),
                byState.getOrDefault(LearningState.RELEARNING, 0L).intValue(),
                (int) all.stream().filter(VocabularyCard::isMature).count(), dueNow, Math.min(newAllowed, newCards));
    }

    private int newAllowedToday(Long userId, Instant now) {
        ZoneId zone = profiles.zoneOf(userId);
        Instant startOfDay = LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();
        long introduced = cards.countByUserIdAndFirstReviewedAtGreaterThanEqual(userId, startOfDay);
        return (int) Math.max(0, newPerDay - introduced);
    }

    private Map<Long, VocabularyItem> itemsOf(List<VocabularyCard> all) {
        return items.findByIdIn(all.stream().map(VocabularyCard::getItemId).distinct().toList()).stream()
                .collect(Collectors.toMap(VocabularyItem::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private static List<VocabularyGradePreview> previews(VocabularyCard c) {
        Sm2.State s = c.state();
        return java.util.Arrays.stream(GRADES).mapToObj(q -> new VocabularyGradePreview(q, Sm2.next(s, q).intervalDays()))
                .toList();
    }

    private static VocabularyCardView view(VocabularyCard c, VocabularyItem i) {
        return new VocabularyCardView(c.getPublicId(), i.getTerm(), i.getPartOfSpeech(), i.getIpa(), i.getTranslationEs(),
                i.getCefrLevel(), c.getLearningState().name(), c.getRepetitions(), c.getIntervalDays(),
                c.getEaseFactor(), c.getLapses(), c.getDueAt(), c.getLastReviewedAt());
    }

    private static String json(Sm2.State s) {
        return "{\"algorithm\":\"" + Sm2.ALGORITHM + "\",\"version\":" + Sm2.VERSION + ",\"ef\":"
                + s.easeFactor().toPlainString() + ",\"repetitions\":" + s.repetitions() + ",\"interval\":"
                + s.intervalDays() + "}";
    }

    private static String clip(String s, int max) {
        String v = s == null ? "" : s.strip();
        return v.length() > max ? v.substring(0, max) : v;
    }
}
