package com.englishos.recommendation.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Motor de recomendaciones por reglas, sin IA (RF-REC-1). Cada regla produce un factor de 0 a 1000 (por mil, para no
 * usar coma flotante) a partir del estado del alumno; la puntuación es {@code peso × factor / 1000}. Los pesos (0–100)
 * los ajusta el administrador (RF-REC-2). Se devuelven las {@value #MAX_RESULTS} de mayor puntuación.
 *
 * <p>Reglas (versión {@value #VERSION}):
 * <ul>
 * <li><b>DIAGNOSTIC</b>: sin diagnóstico → 1000.</li>
 * <li><b>REVIEW_CARDS</b>: con tarjetas vencidas → 500 + 25 por tarjeta (máx. 1000); solo nuevas → 300.</li>
 * <li><b>PRACTICE_ERRORS</b>: con errores abiertos → 300 + 175 por error de la categoría más repetida (máx. 1000).</li>
 * <li><b>NEXT_LESSON</b>: 1000 si cabe en el tiempo que queda hoy; 600 si no; 400 si quedan menos de 5 min.</li>
 * <li><b>WEAK_SKILL</b>: 500 por cada nivel de diferencia entre la habilidad más débil y la más fuerte (máx. 1000).</li>
 * <li><b>CONVERSATION</b>: si puede usar al profesor → 700 con 10 min o más disponibles, 300 con menos.</li>
 * <li>Si ya cumplió su meta diaria, todo excepto los repasos vencidos vale la mitad (descanso sin castigo).</li>
 * </ul>
 */
public final class RecommendationEngine {

    public static final int VERSION = 1;
    public static final int MAX_RESULTS = 4;

    public enum Type { DIAGNOSTIC, REVIEW_CARDS, PRACTICE_ERRORS, NEXT_LESSON, WEAK_SKILL, CONVERSATION }

    /** Clave JSON de cada peso (tabla {@code recommendation_weights}). */
    public static final Map<Type, String> WEIGHT_KEYS = Map.of(
            Type.DIAGNOSTIC, "diagnostic",
            Type.REVIEW_CARDS, "reviews",
            Type.PRACTICE_ERRORS, "recurringErrors",
            Type.NEXT_LESSON, "nextLesson",
            Type.WEAK_SKILL, "weakSkill",
            Type.CONVERSATION, "conversation");

    public record NextLesson(String publicId, String title, int estimatedMinutes) {
    }

    /**
     * Estado del alumno. {@code weakestSkill} es el nombre de la habilidad más débil (o null) y {@code skillGap} la
     * diferencia de niveles MCER entre la más fuerte y la más débil.
     */
    public record Snapshot(boolean hasDiagnostic, int dueCards, int newCardsAvailable, int openErrors,
            String topErrorCategory, int topErrorCount, NextLesson nextLesson, String weakestSkill, int skillGap,
            int minutesLeftToday, boolean canUseTutor) {
    }

    public record Recommendation(Type type, int score, String title, String reason, String actionPath,
            int estimatedMinutes) {
    }

    private RecommendationEngine() {
    }

    public static List<Recommendation> recommend(Snapshot s, Map<Type, Integer> weights) {
        Map<Type, Candidate> c = new EnumMap<>(Type.class);
        boolean goalReached = s.minutesLeftToday() <= 0;

        if (!s.hasDiagnostic()) {
            c.put(Type.DIAGNOSTIC, new Candidate(1000, "Haz tu diagnóstico",
                    "Aún no conocemos tu nivel: el diagnóstico ajusta tu plan y al profesor.", "/app/onboarding", 10));
        }
        if (s.dueCards() > 0) {
            int cards = s.dueCards() + Math.min(s.newCardsAvailable(), 10);
            c.put(Type.REVIEW_CARDS, new Candidate(Math.min(1000, 500 + 25 * s.dueCards()), "Repasa tus palabras",
                    plural(s.dueCards(), "tarjeta vencida", "tarjetas vencidas")
                            + ": repasarlas a tiempo evita que las olvides.", "/app/practice", minutesForCards(cards)));
        } else if (s.newCardsAvailable() > 0) {
            c.put(Type.REVIEW_CARDS, new Candidate(300, "Aprende palabras nuevas",
                    plural(s.newCardsAvailable(), "palabra nueva", "palabras nuevas") + " de tus lecciones te esperan.",
                    "/app/practice", minutesForCards(Math.min(s.newCardsAvailable(), 10))));
        }
        if (s.openErrors() > 0) {
            boolean recurring = s.topErrorCount() >= 2;
            c.put(Type.PRACTICE_ERRORS, new Candidate(Math.min(1000, 300 + 175 * s.topErrorCount()),
                    "Corrige tus errores frecuentes",
                    recurring ? "Repites errores de " + categoryEs(s.topErrorCategory()) + " ("
                            + s.topErrorCount() + "). Practícalos hasta superarlos."
                            : "Practica el error que el profesor te corrigió.",
                    "/app/practice?tab=errors", Math.min(10, Math.max(2, s.openErrors()))));
        }
        if (s.nextLesson() != null) {
            NextLesson l = s.nextLesson();
            int factor = s.minutesLeftToday() < 5 && l.estimatedMinutes() > 5 ? 400
                    : s.minutesLeftToday() >= l.estimatedMinutes() ? 1000 : 600;
            c.put(Type.NEXT_LESSON, new Candidate(factor, "Sigue con tu plan: " + l.title(),
                    "Es la siguiente lección de tu plan (" + l.estimatedMinutes() + " min).",
                    "/app/lessons/" + l.publicId(), l.estimatedMinutes()));
        }
        if (s.weakestSkill() != null && s.skillGap() > 0) {
            c.put(Type.WEAK_SKILL, new Candidate(Math.min(1000, 500 * s.skillGap()),
                    "Refuerza tu " + skillEs(s.weakestSkill()),
                    "Es tu habilidad más baja en el diagnóstico (" + plural(s.skillGap(), "nivel", "niveles")
                            + " por debajo de la más alta).",
                    s.canUseTutor() ? "/app/tutor?mode=GUIDED&topic=" + skillEs(s.weakestSkill()) : "/app/plan", 10));
        }
        if (s.canUseTutor()) {
            c.put(Type.CONVERSATION, new Candidate(s.minutesLeftToday() >= 10 ? 700 : 300, "Conversa con el profesor",
                    "Practicar en contexto fija lo que aprendiste. Se cobra solo lo que consumas.", "/app/tutor", 10));
        }

        List<Recommendation> out = new ArrayList<>();
        for (Map.Entry<Type, Candidate> e : c.entrySet()) {
            int factor = e.getValue().factor();
            boolean exempt = e.getKey() == Type.REVIEW_CARDS && s.dueCards() > 0;
            if (goalReached && !exempt) {
                factor /= 2;
            }
            int weight = Math.max(0, Math.min(100, weights.getOrDefault(e.getKey(), 0)));
            int score = weight * factor / 1000;
            if (score > 0) {
                Candidate v = e.getValue();
                out.add(new Recommendation(e.getKey(), score, v.title(), v.reason(), v.path(), v.minutes()));
            }
        }
        out.sort(Comparator.comparingInt(Recommendation::score).reversed()
                .thenComparing(Recommendation::type));
        return out.size() > MAX_RESULTS ? List.copyOf(out.subList(0, MAX_RESULTS)) : List.copyOf(out);
    }

    private record Candidate(int factor, String title, String reason, String path, int minutes) {
    }

    /** ~15 s por tarjeta. */
    static int minutesForCards(int cards) {
        return Math.max(1, (cards + 3) / 4);
    }

    private static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    static String skillEs(String skill) {
        return switch (skill) {
            case "VOCABULARY" -> "vocabulario";
            case "GRAMMAR" -> "gramática";
            case "READING" -> "lectura";
            case "WRITING" -> "escritura";
            case "LISTENING" -> "comprensión auditiva";
            case "SPEAKING" -> "expresión oral";
            default -> skill.toLowerCase(java.util.Locale.ROOT);
        };
    }

    static String categoryEs(String category) {
        if (category == null) {
            return "varios tipos";
        }
        return switch (category) {
            case "GRAMMAR" -> "gramática";
            case "VOCABULARY" -> "vocabulario";
            case "SPELLING" -> "ortografía";
            case "WORD_ORDER" -> "orden de palabras";
            default -> "otros tipos";
        };
    }
}
