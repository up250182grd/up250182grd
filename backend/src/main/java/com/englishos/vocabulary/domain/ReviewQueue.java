package com.englishos.vocabulary.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Cola diaria de repaso (RF-SRS-3): primero las olvidadas (RELEARNING), después las vencidas de la más atrasada a la
 * menos, y al final tarjetas nuevas hasta el cupo diario. Así el alumno no se satura de palabras nuevas mientras
 * tenga repasos pendientes.
 */
public final class ReviewQueue {

    private ReviewQueue() {
    }

    public static List<VocabularyCard> build(List<VocabularyCard> cards, Instant now, int newAllowedToday, int max) {
        Comparator<VocabularyCard> byDue = Comparator.comparing(VocabularyCard::getDueAt)
                .thenComparing(VocabularyCard::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        List<VocabularyCard> out = new ArrayList<>();
        cards.stream()
                .filter(c -> c.getLearningState() == VocabularyCard.LearningState.RELEARNING && !c.getDueAt().isAfter(now))
                .sorted(byDue).forEach(out::add);
        cards.stream()
                .filter(c -> c.getLearningState() != VocabularyCard.LearningState.RELEARNING
                        && c.getLearningState() != VocabularyCard.LearningState.NEW && !c.getDueAt().isAfter(now))
                .sorted(byDue).forEach(out::add);
        cards.stream()
                .filter(c -> c.getLearningState() == VocabularyCard.LearningState.NEW)
                .sorted(byDue).limit(Math.max(0, newAllowedToday)).forEach(out::add);
        return out.size() > max ? List.copyOf(out.subList(0, max)) : List.copyOf(out);
    }
}
