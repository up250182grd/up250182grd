package com.englishos.recommendation.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.englishos.recommendation.application.ActivityEstimator.Event;
import com.englishos.recommendation.application.ActivityEstimator.Kind;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityEstimatorTest {

    private static final ZoneId MX = ZoneId.of("America/Mexico_City");

    @Test
    void groupsByLocalDayIncludingEmptyDaysAndCapsLongEvents() {
        LocalDate from = LocalDate.of(2026, 11, 9);
        LocalDate to = LocalDate.of(2026, 11, 11);
        List<Event> events = List.of(
                // 2026-11-11 05:00Z = 10 de noviembre 23:00 en CDMX
                new Event(Instant.parse("2026-11-11T05:00:00Z"), 90_000, 30_000, Kind.EXERCISE),
                new Event(Instant.parse("2026-11-11T18:00:00Z"), null, 15_000, Kind.REVIEW),
                new Event(Instant.parse("2026-11-11T18:01:00Z"), 3_600_000, 15_000, Kind.REVIEW), // tope 5 min
                new Event(Instant.parse("2026-11-11T18:02:00Z"), null, 60_000, Kind.TUTOR_MESSAGE),
                new Event(Instant.parse("2026-10-01T18:00:00Z"), null, 30_000, Kind.EXERCISE)); // fuera de rango

        List<ActivityEstimator.Day> days = ActivityEstimator.byDay(events, from, to, MX);

        assertThat(days).extracting(ActivityEstimator.Day::date).containsExactly(from, from.plusDays(1), to);
        assertThat(days.get(0).minutes()).isZero();
        assertThat(days.get(1).exercises()).isEqualTo(1);
        assertThat(days.get(1).minutes()).isEqualTo(2);  // 90 s → 1.5 → 2
        assertThat(days.get(2).reviews()).isEqualTo(2);
        assertThat(days.get(2).tutorMessages()).isEqualTo(1);
        assertThat(days.get(2).minutes()).isEqualTo(6);  // 15 s + 300 s + 60 s = 375 s → 6.25 → 6
    }
}
