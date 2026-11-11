package com.englishos.gamification.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Racha de estudio (RN-45, RF-GAM-2), versión 1.
 *
 * <ul>
 * <li>Un día cuenta si se hizo una actividad significativa: ≥ 5 min, o 1 lección terminada, o 10 repasos.</li>
 * <li>Se permite <b>1 día de descanso por semana</b> (lunes a domingo) sin romper la racha. El descanso no suma.</li>
 * <li>Un segundo día sin actividad en la misma semana rompe la racha. Sin castigos por errores.</li>
 * <li>Hoy, mientras no se cumpla, está "pendiente": no rompe la racha hasta que termine el día.</li>
 * </ul>
 */
public final class Streak {

    private Streak() {
    }

    public enum DayStatus { QUALIFIED, REST, MISSED, PENDING, NONE }

    /** Umbrales de un día significativo. */
    public static final int MIN_MINUTES = 5;
    public static final int MIN_LESSONS = 1;
    public static final int MIN_REVIEWS = 10;

    public static boolean qualifies(int minutes, int lessons, int reviews) {
        return minutes >= MIN_MINUTES || lessons >= MIN_LESSONS || reviews >= MIN_REVIEWS;
    }

    public record Result(int current, int best, boolean todayQualified, boolean restUsedThisWeek,
            Map<LocalDate, DayStatus> days) {
    }

    static LocalDate weekOf(LocalDate d) {
        return d.with(DayOfWeek.MONDAY);
    }

    /**
     * Calcula la racha actual y la mejor recorriendo los días desde el primero con actividad hasta hoy.
     *
     * @param qualified días (en la zona del alumno) que cumplieron la condición
     */
    public static Result compute(Set<LocalDate> qualified, LocalDate today) {
        SortedSet<LocalDate> sorted = new TreeSet<>(qualified);
        Map<LocalDate, DayStatus> days = new LinkedHashMap<>();
        boolean todayQualified = qualified.contains(today);
        if (sorted.isEmpty() || sorted.first().isAfter(today)) {
            days.put(today, DayStatus.PENDING);
            return new Result(0, 0, false, false, days);
        }
        int run = 0;
        int best = 0;
        Set<LocalDate> restWeeks = new HashSet<>();
        for (LocalDate d = sorted.first(); !d.isAfter(today); d = d.plusDays(1)) {
            if (qualified.contains(d)) {
                run++;
                best = Math.max(best, run);
                days.put(d, DayStatus.QUALIFIED);
            } else if (d.equals(today)) {
                days.put(d, DayStatus.PENDING);
            } else if (run > 0 && restWeeks.add(weekOf(d))) {
                days.put(d, DayStatus.REST);
            } else {
                days.put(d, run > 0 ? DayStatus.MISSED : DayStatus.NONE);
                run = 0;
                restWeeks.clear();
            }
        }
        return new Result(run, best, todayQualified, restWeeks.contains(weekOf(today)), days);
    }
}
