package com.englishos.recommendation.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.englishos.recommendation.domain.RecommendationEngine.NextLesson;
import com.englishos.recommendation.domain.RecommendationEngine.Recommendation;
import com.englishos.recommendation.domain.RecommendationEngine.Snapshot;
import com.englishos.recommendation.domain.RecommendationEngine.Type;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Perfiles de alumno representativos → recomendaciones esperadas (RF-REC-1). */
class RecommendationEngineTest {

    private static final NextLesson LESSON = new NextLesson("L1", "Verb to be", 12);

    private static Map<Type, Integer> defaults() {
        Map<Type, Integer> w = new EnumMap<>(Type.class);
        w.put(Type.DIAGNOSTIC, 100);
        w.put(Type.REVIEW_CARDS, 80);
        w.put(Type.NEXT_LESSON, 70);
        w.put(Type.PRACTICE_ERRORS, 60);
        w.put(Type.WEAK_SKILL, 45);
        w.put(Type.CONVERSATION, 35);
        return w;
    }

    private static List<Type> types(List<Recommendation> r) {
        return r.stream().map(Recommendation::type).toList();
    }

    @Test
    void newStudentWithoutDiagnosticIsSentToTheDiagnosticFirst() {
        var s = new Snapshot(false, 0, 0, 0, null, 0, LESSON, null, 0, 20, false);
        List<Recommendation> r = RecommendationEngine.recommend(s, defaults());
        assertThat(types(r)).containsExactly(Type.DIAGNOSTIC, Type.NEXT_LESSON);
        assertThat(r.get(0).score()).isEqualTo(100);
        assertThat(r.get(0).actionPath()).isEqualTo("/app/onboarding");
    }

    @Test
    void manyOverdueCardsComeBeforeTheNextLesson() {
        var s = new Snapshot(true, 25, 5, 0, null, 0, LESSON, null, 0, 20, true);
        List<Recommendation> r = RecommendationEngine.recommend(s, defaults());
        assertThat(types(r)).containsExactly(Type.REVIEW_CARDS, Type.NEXT_LESSON, Type.CONVERSATION);
        assertThat(r.get(0).score()).isEqualTo(80);     // 80 × 1000/1000
        assertThat(r.get(0).reason()).startsWith("25 tarjetas vencidas");
        assertThat(r.get(0).estimatedMinutes()).isEqualTo(8); // (25 + 5) tarjetas ≈ 15 s c/u
    }

    @Test
    void recurringErrorsOutrankAFewReviews() {
        var s = new Snapshot(true, 2, 0, 5, "GRAMMAR", 4, null, null, 0, 20, false);
        List<Recommendation> r = RecommendationEngine.recommend(s, defaults());
        assertThat(types(r)).containsExactly(Type.PRACTICE_ERRORS, Type.REVIEW_CARDS);
        assertThat(r.get(0).score()).isEqualTo(60);     // 300 + 4×175 = 1000
        assertThat(r.get(1).score()).isEqualTo(44);     // 80 × 550/1000
        assertThat(r.get(0).reason()).contains("gramática (4)");
    }

    @Test
    void weakSkillSuggestsGuidedTutorPracticeWhenAffordableAndThePlanOtherwise() {
        var withTutor = new Snapshot(true, 0, 0, 0, null, 0, null, "READING", 2, 30, true);
        Recommendation weak = RecommendationEngine.recommend(withTutor, defaults()).stream()
                .filter(x -> x.type() == Type.WEAK_SKILL).findFirst().orElseThrow();
        assertThat(weak.score()).isEqualTo(45);
        assertThat(weak.actionPath()).startsWith("/app/tutor?mode=GUIDED");
        assertThat(weak.title()).isEqualTo("Refuerza tu lectura");

        var noTutor = new Snapshot(true, 0, 0, 0, null, 0, null, "READING", 1, 30, false);
        assertThat(RecommendationEngine.recommend(noTutor, defaults()).getFirst().actionPath()).isEqualTo("/app/plan");
    }

    @Test
    void littleTimeLeftLowersLongLessons() {
        var plenty = new Snapshot(true, 0, 0, 0, null, 0, LESSON, null, 0, 30, false);
        var short3 = new Snapshot(true, 0, 0, 0, null, 0, LESSON, null, 0, 3, false);
        assertThat(RecommendationEngine.recommend(plenty, defaults()).getFirst().score()).isEqualTo(70);
        assertThat(RecommendationEngine.recommend(short3, defaults()).getFirst().score()).isEqualTo(28); // 70 × 0.4
    }

    @Test
    void afterTheDailyGoalOnlyDueReviewsKeepFullWeight() {
        var s = new Snapshot(true, 10, 0, 0, null, 0, LESSON, null, 0, 0, false);
        List<Recommendation> r = RecommendationEngine.recommend(s, defaults());
        assertThat(types(r)).containsExactly(Type.REVIEW_CARDS, Type.NEXT_LESSON);
        assertThat(r.get(0).score()).isEqualTo(60);     // 80 × 750/1000, sin reducción
        assertThat(r.get(1).score()).isEqualTo(14);     // 70 × (400/2)/1000
    }

    @Test
    void weightsAreConfigurableAndZeroDisablesARule() {
        Map<Type, Integer> w = defaults();
        w.put(Type.REVIEW_CARDS, 0);
        w.put(Type.CONVERSATION, 100);
        var s = new Snapshot(true, 25, 0, 0, null, 0, LESSON, null, 0, 20, true);
        assertThat(types(RecommendationEngine.recommend(s, w))).containsExactly(Type.NEXT_LESSON, Type.CONVERSATION);
    }

    @Test
    void returnsAtMostFourSuggestions() {
        var s = new Snapshot(false, 5, 5, 3, "VOCABULARY", 3, LESSON, "GRAMMAR", 1, 30, true);
        assertThat(RecommendationEngine.recommend(s, defaults())).hasSize(RecommendationEngine.MAX_RESULTS);
    }
}
