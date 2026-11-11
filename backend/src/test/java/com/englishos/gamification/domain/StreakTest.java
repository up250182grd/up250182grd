package com.englishos.gamification.domain;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import com.englishos.gamification.domain.Streak.DayStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tabla de casos de la racha (RN-45). Fechas de noviembre de 2026: el lunes 2, el 9 y el 16 empiezan semana; el
 * miércoles 18 es "hoy".
 */
class StreakTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18); // miércoles

    private static Set<LocalDate> days(String spec) {
        if (spec.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(spec.split(" ")).map(d -> LocalDate.of(2026, 11, Integer.parseInt(d)))
                .collect(Collectors.toSet());
    }

    @ParameterizedTest(name = "[{0}] → actual {1}, mejor {2}")
    @CsvSource(delimiter = '|', value = {
            // días con actividad         | actual | mejor | descripción
            "''                           | 0 | 0",   // sin actividad
            "18                           | 1 | 1",   // solo hoy
            "17                           | 1 | 1",   // ayer; hoy pendiente no rompe
            "14 15 16 17 18               | 5 | 5",   // seguidos
            "14 15 17 18                  | 4 | 4",   // lunes 16 = descanso de su semana
            "13 14 16 18                  | 4 | 4",   // dom 15 descanso (semana 9) y mar 17 descanso (semana 16)
            "14 15 18                     | 1 | 2",   // lun 16 descanso, mar 17 segundo hueco en la semana: se rompe
            "10 11 12 13 14 15 16 17      | 8 | 8",   // hoy pendiente
            "2 3 4 5 6 7 8 10 11          | 0 | 9",   // jue 12 descanso, vie 13 rompe (misma semana): sin racha hoy
    })
    void streakTable(String spec, int current, int best) {
        Streak.Result r = Streak.compute(days(spec.trim()), TODAY);
        assertThat(r.current()).isEqualTo(current);
        assertThat(r.best()).isEqualTo(best);
    }

    @Test
    void reportsDayStatusesForTheCalendar() {
        Streak.Result r = Streak.compute(days("14 15 17"), TODAY);
        assertThat(r.days()).containsEntry(LocalDate.of(2026, 11, 14), DayStatus.QUALIFIED)
                .containsEntry(LocalDate.of(2026, 11, 16), DayStatus.REST)
                .containsEntry(LocalDate.of(2026, 11, 17), DayStatus.QUALIFIED)
                .containsEntry(TODAY, DayStatus.PENDING);
        assertThat(r.restUsedThisWeek()).isTrue();
        assertThat(r.todayQualified()).isFalse();
        assertThat(r.current()).isEqualTo(3);
    }

    @Test
    void aBrokenStreakStartsOverAndTheRestDayIsPerWeek() {
        // Semana del 9: descanso el 10 y nuevo hueco el 11 → se rompe; luego 12..18 seguidos.
        Streak.Result r = Streak.compute(days("9 12 13 14 15 16 17 18"), TODAY);
        assertThat(r.days()).containsEntry(LocalDate.of(2026, 11, 10), DayStatus.REST)
                .containsEntry(LocalDate.of(2026, 11, 11), DayStatus.MISSED);
        assertThat(r.current()).isEqualTo(7);
        assertThat(r.best()).isEqualTo(7);
        assertThat(r.todayQualified()).isTrue();
    }

    @Test
    void significantDayThresholds() {
        assertThat(Streak.qualifies(5, 0, 0)).isTrue();
        assertThat(Streak.qualifies(4, 0, 9)).isFalse();
        assertThat(Streak.qualifies(0, 1, 0)).isTrue();
        assertThat(Streak.qualifies(0, 0, 10)).isTrue();
    }
}
