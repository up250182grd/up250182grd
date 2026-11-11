package com.englishos.recommendation.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minutos de estudio ESTIMADOS a partir de la actividad registrada (no hay un cronómetro de sesión). Cada evento cuenta
 * su duración si se conoce, o un valor típico si no; ninguno cuenta más de 5 minutos.
 */
final class ActivityEstimator {

    static final int EXERCISE_DEFAULT_MS = 30_000;
    static final int REVIEW_DEFAULT_MS = 15_000;
    static final int TUTOR_MESSAGE_MS = 60_000;
    static final int MAX_EVENT_MS = 300_000;

    record Event(Instant at, Integer durationMs, int defaultMs, Kind kind) {
    }

    enum Kind { EXERCISE, REVIEW, TUTOR_MESSAGE }

    record Day(LocalDate date, int exercises, int reviews, int tutorMessages, int minutes) {
    }

    private ActivityEstimator() {
    }

    static int millis(Event e) {
        int ms = e.durationMs() == null || e.durationMs() <= 0 ? e.defaultMs() : e.durationMs();
        return Math.min(ms, MAX_EVENT_MS);
    }

    /** Agrupa por día local del alumno, incluyendo los días sin actividad (para la gráfica). */
    static List<Day> byDay(List<Event> events, LocalDate from, LocalDate to, ZoneId zone) {
        Map<LocalDate, int[]> acc = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            acc.put(d, new int[4]);
        }
        for (Event e : events) {
            int[] a = acc.get(LocalDate.ofInstant(e.at(), zone));
            if (a == null) {
                continue;
            }
            a[e.kind().ordinal()]++;
            a[3] += millis(e);
        }
        return acc.entrySet().stream()
                .map(x -> new Day(x.getKey(), x.getValue()[0], x.getValue()[1], x.getValue()[2],
                        (x.getValue()[3] + 30_000) / 60_000))
                .toList();
    }
}
