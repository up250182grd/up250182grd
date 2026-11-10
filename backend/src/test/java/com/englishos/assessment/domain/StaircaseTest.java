package com.englishos.assessment.domain;

import java.util.function.BiPredicate;

import com.englishos.assessment.domain.Staircase.Confidence;
import com.englishos.assessment.domain.Staircase.Params;
import com.englishos.assessment.domain.Staircase.State;
import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.Skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RN-42: escalera adaptativa por habilidad. */
class StaircaseTest {

    private final Params p = Params.DEFAULT;

    /** Simula un alumno que responde según {@code knows(skill, level)} hasta terminar. */
    private State simulate(BiPredicate<Skill, CefrLevel> knows) {
        State s = Staircase.initial(p);
        int guard = 0;
        Skill skill;
        while ((skill = Staircase.nextSkill(s)) != null && guard++ < 100) {
            CefrLevel level = s.track(skill).current();
            s = Staircase.record(s, skill, level, knows.test(skill, level), p);
        }
        return s;
    }

    @Test
    void startsAtA2ForEverySkillAndInterleavesSkills() {
        State s = Staircase.initial(p);
        assertThat(s.tracks()).allSatisfy(t -> assertThat(t.current()).isEqualTo(CefrLevel.A2));
        Skill first = Staircase.nextSkill(s);
        s = Staircase.record(s, first, CefrLevel.A2, true, p);
        assertThat(Staircase.nextSkill(s)).isNotEqualTo(first);
    }

    @Test
    void twoCorrectAnswersMoveUpAndTwoWrongMoveDown() {
        State s = Staircase.initial(p);
        s = Staircase.record(s, Skill.GRAMMAR, CefrLevel.A2, true, p);
        assertThat(s.track(Skill.GRAMMAR).current()).isEqualTo(CefrLevel.A2);
        s = Staircase.record(s, Skill.GRAMMAR, CefrLevel.A2, true, p);
        assertThat(s.track(Skill.GRAMMAR).current()).isEqualTo(CefrLevel.B1);
        s = Staircase.record(s, Skill.GRAMMAR, CefrLevel.B1, false, p);
        s = Staircase.record(s, Skill.GRAMMAR, CefrLevel.B1, false, p);
        assertThat(s.track(Skill.GRAMMAR).current()).isEqualTo(CefrLevel.A2);
        assertThat(s.track(Skill.GRAMMAR).reversals()).isEqualTo(1);
    }

    @Test
    void strongLearnerReachesTheTopOfTheBank() {
        State s = simulate((skill, level) -> true);
        assertThat(s.isFinished()).isTrue();
        for (var t : s.tracks()) {
            var e = Staircase.estimate(t, p);
            assertThat(e.level()).isEqualTo(CefrLevel.B2);
            assertThat(t.answered()).isEqualTo(p.maxItemsPerSkill());
            assertThat(t.current()).isEqualTo(p.max()); // nunca supera el rango del banco
        }
    }

    @Test
    void beginnerIsPlacedAtA1WithLowConfidence() {
        State s = simulate((skill, level) -> false);
        for (var t : s.tracks()) {
            var e = Staircase.estimate(t, p);
            assertThat(e.level()).isEqualTo(CefrLevel.A1);
            assertThat(e.confidence()).isEqualTo(Confidence.LOW);
            assertThat(t.current()).isEqualTo(CefrLevel.A1); // nunca baja de A1
        }
    }

    @Test
    void learnerWhoKnowsUpToB1ConvergesToB1() {
        State s = simulate((skill, level) -> level.compareTo(CefrLevel.B1) <= 0);
        for (var t : s.tracks()) {
            var e = Staircase.estimate(t, p);
            assertThat(e.level()).as(t.skill().name()).isEqualTo(CefrLevel.B1);
            assertThat(e.evidence()).isGreaterThanOrEqualTo(6);
            assertThat(e.confidence()).isIn(Confidence.MEDIUM, Confidence.HIGH);
        }
    }

    @Test
    void skillsAreEstimatedIndependently() {
        State s = simulate((skill, level) -> skill == Skill.VOCABULARY || level == CefrLevel.A1);
        assertThat(Staircase.estimate(s.track(Skill.VOCABULARY), p).level()).isEqualTo(CefrLevel.B2);
        assertThat(Staircase.estimate(s.track(Skill.GRAMMAR), p).level()).isEqualTo(CefrLevel.A1);
    }

    @Test
    void finishedSkillCannotBeRecordedAgain() {
        State s = Staircase.finish(Staircase.initial(p), Skill.READING);
        assertThat(s.track(Skill.READING).finished()).isTrue();
        State finalS = s;
        assertThatThrownBy(() -> Staircase.record(finalS, Skill.READING, CefrLevel.A2, true, p))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void levelNeedsEnoughEvidence() {
        // Un solo acierto en B2 no basta para estimar B2.
        State s = Staircase.initial(p);
        s = Staircase.record(s, Skill.GRAMMAR, CefrLevel.B2, true, p);
        assertThat(Staircase.estimate(s.track(Skill.GRAMMAR), p).level()).isEqualTo(CefrLevel.A1);
    }
}
