package com.englishos.learning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.englishos.learning.domain.MasteryPolicy.RunResult;
import com.englishos.learning.domain.MasteryPolicy.State;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RN-40: dominio = ≥ 80 % en 2 días distintos y última ejecución ≥ 70 %. */
class MasteryPolicyTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 9);
    private static final LocalDate TUE = MON.plusDays(1);
    private final MasteryPolicy policy = MasteryPolicy.DEFAULT;

    private static RunResult run(LocalDate d, String score, long seq) {
        return new RunResult(d, new BigDecimal(score), seq);
    }

    @Test
    void noRunsOrLowScoresAreInProgress() {
        assertThat(policy.evaluate(List.of())).isEqualTo(State.IN_PROGRESS);
        assertThat(policy.evaluate(List.of(run(MON, "0.60", 1)))).isEqualTo(State.IN_PROGRESS);
    }

    @Test
    void oneGoodRunIsPracticedNotMastered() {
        assertThat(policy.evaluate(List.of(run(MON, "1.00", 1)))).isEqualTo(State.PRACTICED);
    }

    @Test
    void twoGoodRunsTheSameDayAreNotEnough() {
        assertThat(policy.evaluate(List.of(run(MON, "1.00", 1), run(MON, "0.90", 2)))).isEqualTo(State.PRACTICED);
    }

    @Test
    void goodRunsOnTwoDifferentDaysMeanMastery() {
        assertThat(policy.evaluate(List.of(run(MON, "0.80", 1), run(TUE, "0.85", 2)))).isEqualTo(State.MASTERED);
    }

    @Test
    void aWeakLastRunPreventsMastery() {
        assertThat(policy.evaluate(List.of(run(MON, "0.90", 1), run(TUE, "0.90", 2), run(TUE, "0.60", 3))))
                .isEqualTo(State.PRACTICED);
        assertThat(policy.evaluate(List.of(run(MON, "0.90", 1), run(TUE, "0.90", 2), run(TUE, "0.70", 3))))
                .isEqualTo(State.MASTERED);
    }

    @Test
    void boundaryJustBelowPassScoreDoesNotCount() {
        assertThat(policy.evaluate(List.of(run(MON, "0.7999", 1), run(TUE, "0.90", 2)))).isEqualTo(State.PRACTICED);
    }
}
