package com.englishos.vocabulary.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tabla de casos de SM-2 (RN-43), calculados a mano a partir de la fórmula. */
class Sm2Test {

    @ParameterizedTest(name = "EF {0}, q={1} → EF {2}")
    @CsvSource({
            "2.500, 5, 2.600",  // +0.1
            "2.500, 4, 2.500",  // +0.1 − 1·(0.08+0.02) = 0
            "2.500, 3, 2.360",  // +0.1 − 2·(0.08+0.04) = −0.14
            "1.300, 3, 1.300",  // nunca baja de 1.3
            "1.350, 3, 1.300",
            "2.000, 5, 2.100",
    })
    void easeFactorFollowsTheFormula(String ef, int q, String expected) {
        assertThat(Sm2.nextEaseFactor(new BigDecimal(ef), q)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{0} rep={1} int={2} q={3} → rep={4} int={5} EF={6}")
    @CsvSource({
            // ef,   rep, int, q, rep', int', ef'
            "2.500, 0, 0,  4, 1, 1,  2.500",  // primera vez: 1 día
            "2.500, 1, 1,  4, 2, 6,  2.500",  // segunda: 6 días
            "2.500, 2, 6,  4, 3, 15, 2.500",  // 6 × 2.5 = 15
            "2.500, 3, 15, 5, 4, 38, 2.600",  // 15 × 2.5 = 37.5 → 38 (EF anterior, mitad hacia arriba)
            "2.360, 2, 6,  3, 3, 14, 2.220",  // 6 × 2.36 = 14.16 → 14
            "2.600, 4, 38, 2, 0, 1,  2.600",  // olvido: reinicia, EF sin cambio
            "1.300, 5, 20, 0, 0, 1,  1.300",
    })
    void intervalsFollowSm2(String ef, int rep, int interval, int q, int expRep, int expInterval, String expEf) {
        Sm2.State next = Sm2.next(new Sm2.State(new BigDecimal(ef), rep, interval), q);
        assertThat(next.repetitions()).isEqualTo(expRep);
        assertThat(next.intervalDays()).isEqualTo(expInterval);
        assertThat(next.easeFactor()).isEqualByComparingTo(expEf);
    }

    @Test
    void aFullSequenceGrowsAndResets() {
        Sm2.State s = Sm2.State.initial();
        int[] expected = {1, 6, 16, 45};
        int[] qualities = {5, 5, 5, 5};
        for (int i = 0; i < qualities.length; i++) {
            s = Sm2.next(s, qualities[i]);
            assertThat(s.intervalDays()).as("paso %d", i + 1).isEqualTo(expected[i]);
        }
        // 6 × 2.7 = 16.2 → 16; 16 × 2.8 = 44.8 → 45
        assertThat(s.easeFactor()).isEqualByComparingTo("2.900");
        s = Sm2.next(s, 1);
        assertThat(s.repetitions()).isZero();
        assertThat(s.intervalDays()).isEqualTo(1);
    }

    @Test
    void rejectsQualityOutOfRange() {
        assertThatThrownBy(() -> Sm2.next(Sm2.State.initial(), 6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sm2.next(Sm2.State.initial(), -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
