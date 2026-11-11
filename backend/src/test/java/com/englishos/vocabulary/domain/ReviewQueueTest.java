package com.englishos.vocabulary.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewQueueTest {

    private static final ZoneId MX = ZoneId.of("America/Mexico_City");
    private static final Instant NOW = Instant.parse("2026-11-11T18:00:00Z");

    private static VocabularyCard card(long itemId, Instant createdAt) {
        return VocabularyCard.create(1L, itemId, createdAt);
    }

    @Test
    void forgottenCardsComeFirstThenOverdueThenNewWithinTheDailyQuota() {
        Instant tenDaysAgo = NOW.minus(Duration.ofDays(10));
        VocabularyCard overdue = card(1, tenDaysAgo);
        overdue.review(5, tenDaysAgo, MX);           // vence en 1 día → atrasada 9 días
        VocabularyCard forgotten = card(2, tenDaysAgo);
        forgotten.review(5, tenDaysAgo, MX);
        forgotten.review(1, NOW.minus(Duration.ofDays(2)), MX); // RELEARNING, vencida ayer
        VocabularyCard notDue = card(3, NOW);
        notDue.review(5, NOW, MX);                   // vence mañana
        VocabularyCard new1 = card(4, NOW.minusSeconds(30));
        VocabularyCard new2 = card(5, NOW.minusSeconds(20));
        VocabularyCard new3 = card(6, NOW.minusSeconds(10));

        List<VocabularyCard> q = ReviewQueue.build(List.of(new3, notDue, overdue, new1, forgotten, new2), NOW, 2, 50);

        assertThat(q).containsExactly(forgotten, overdue, new1, new2);
        assertThat(forgotten.getLearningState()).isEqualTo(VocabularyCard.LearningState.RELEARNING);
        assertThat(forgotten.getLapses()).isEqualTo(1);
    }

    @Test
    void respectsTheMaximumAndAZeroNewQuota() {
        VocabularyCard a = card(1, NOW.minusSeconds(10));
        VocabularyCard b = card(2, NOW.minusSeconds(5));
        assertThat(ReviewQueue.build(List.of(a, b), NOW, 0, 50)).isEmpty();
        assertThat(ReviewQueue.build(List.of(a, b), NOW, 10, 1)).containsExactly(a);
    }

    @Test
    void dueDateIsTheStartOfTheLocalDay() {
        VocabularyCard c = card(1, NOW);
        c.review(4, NOW, MX); // 12:00 en CDMX → vence mañana 00:00 CDMX
        assertThat(c.getDueAt()).isEqualTo(Instant.parse("2026-11-12T06:00:00Z"));
        assertThat(c.getLearningState()).isEqualTo(VocabularyCard.LearningState.LEARNING);
        c.review(4, c.getDueAt(), MX);
        assertThat(c.getLearningState()).isEqualTo(VocabularyCard.LearningState.REVIEW);
        assertThat(c.getIntervalDays()).isEqualTo(6);
    }
}
