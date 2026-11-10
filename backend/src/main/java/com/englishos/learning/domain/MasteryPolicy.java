package com.englishos.learning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regla de dominio de una lección (RN-40), configurable y comprobable. No basta con un buen resultado aislado:
 * se exige repetir el desempeño en días distintos (recuperación espaciada).
 *
 * <ul>
 *   <li>MASTERED: ≥ {@code requiredDays} días distintos con puntaje ≥ {@code passScore}, y la última ejecución
 *       ≥ {@code minLastScore}.</li>
 *   <li>PRACTICED: al menos una ejecución con puntaje ≥ {@code passScore}.</li>
 *   <li>IN_PROGRESS: en cualquier otro caso con al menos una ejecución terminada.</li>
 * </ul>
 */
public record MasteryPolicy(BigDecimal passScore, int requiredDays, BigDecimal minLastScore) {

    public static final MasteryPolicy DEFAULT = new MasteryPolicy(new BigDecimal("0.80"), 2, new BigDecimal("0.70"));

    public enum State { IN_PROGRESS, PRACTICED, MASTERED }

    public record RunResult(LocalDate date, BigDecimal score, long sequence) {
    }

    public State evaluate(List<RunResult> runs) {
        if (runs.isEmpty()) {
            return State.IN_PROGRESS;
        }
        Set<LocalDate> passingDays = runs.stream()
                .filter(r -> r.score().compareTo(passScore) >= 0)
                .map(RunResult::date)
                .collect(Collectors.toSet());
        RunResult last = runs.stream().max(Comparator.comparingLong(RunResult::sequence)).orElseThrow();
        if (passingDays.size() >= requiredDays && last.score().compareTo(minLastScore) >= 0) {
            return State.MASTERED;
        }
        return passingDays.isEmpty() ? State.IN_PROGRESS : State.PRACTICED;
    }
}
