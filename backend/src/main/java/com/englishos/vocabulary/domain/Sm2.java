package com.englishos.vocabulary.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Algoritmo SM-2 (RN-43, RF-SRS-2), versión {@value #VERSION}. Aritmética decimal exacta: sin coma flotante.
 *
 * <ul>
 * <li>Calidad q ∈ 0..5.</li>
 * <li>q &lt; 3 (olvido): repeticiones = 0, intervalo = 1 día; el factor de facilidad NO cambia (como en la
 * descripción original de SuperMemo: "start repetitions from the beginning without changing the E-Factor").</li>
 * <li>q ≥ 3: repeticiones + 1; intervalo = 1 (rep 1), 6 (rep 2) o intervalo anterior × EF anterior, redondeado al
 * día más cercano (mitades hacia arriba); después EF' = max(1.3, EF + 0.1 − (5−q)·(0.08 + (5−q)·0.02)).</li>
 * </ul>
 */
public final class Sm2 {

    public static final String ALGORITHM = "SM2";
    public static final int VERSION = 1;
    public static final BigDecimal INITIAL_EF = new BigDecimal("2.500");
    public static final BigDecimal MIN_EF = new BigDecimal("1.300");

    private static final BigDecimal P01 = new BigDecimal("0.1");
    private static final BigDecimal P08 = new BigDecimal("0.08");
    private static final BigDecimal P02 = new BigDecimal("0.02");

    private Sm2() {
    }

    public record State(BigDecimal easeFactor, int repetitions, int intervalDays) {

        public static State initial() {
            return new State(INITIAL_EF, 0, 0);
        }
    }

    public static State next(State s, int quality) {
        if (quality < 0 || quality > 5) {
            throw new IllegalArgumentException("La calidad debe estar entre 0 y 5");
        }
        if (quality < 3) {
            return new State(s.easeFactor(), 0, 1);
        }
        int repetitions = s.repetitions() + 1;
        int interval = switch (repetitions) {
            case 1 -> 1;
            case 2 -> 6;
            default -> BigDecimal.valueOf(Math.max(1, s.intervalDays())).multiply(s.easeFactor())
                    .setScale(0, RoundingMode.HALF_UP).intValueExact();
        };
        return new State(nextEaseFactor(s.easeFactor(), quality), repetitions, interval);
    }

    static BigDecimal nextEaseFactor(BigDecimal ef, int quality) {
        BigDecimal d = BigDecimal.valueOf(5 - quality);
        BigDecimal next = ef.add(P01).subtract(d.multiply(P08.add(d.multiply(P02))));
        return next.max(MIN_EF).setScale(3, RoundingMode.HALF_UP);
    }
}
