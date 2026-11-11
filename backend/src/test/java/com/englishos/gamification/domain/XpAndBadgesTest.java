package com.englishos.gamification.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class XpAndBadgesTest {

    @ParameterizedTest(name = "ejecución {0} del día → {1} XP")
    @CsvSource({"1, 20", "2, 10", "3, 5", "4, 2", "5, 2", "40, 2"})
    void repeatingTheSameLessonTheSameDayGivesDecreasingXp(int nth, int xp) {
        assertThat(XpRules.lessonRun(nth)).isEqualTo(xp);
    }

    @Test
    void badgesFollowRealMilestones() {
        var none = new Badges.Stats(0, 0, 0, 0, 0, false, 0);
        assertThat(Badges.ALL).noneMatch(b -> b.earned(none));

        var some = new Badges.Stats(3, 1, 12, 4, 1, true, 7);
        assertThat(Badges.ALL.stream().filter(b -> b.earned(some)).map(Badges.Badge::code))
                .containsExactlyInAnyOrder("DIAGNOSTIC", "FIRST_LESSON", "FIRST_MASTERY", "WORDS_10",
                        "FIRST_CONVERSATION", "STREAK_7");
        Badges.Badge errors5 = Badges.ALL.stream().filter(b -> b.code().equals("ERRORS_5")).findFirst().orElseThrow();
        assertThat(errors5.progressOf(some)).isEqualTo(4);
        assertThat(Badges.ALL).hasSizeBetween(5, 10);
    }
}
