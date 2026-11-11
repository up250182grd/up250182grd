package com.englishos.gamification.domain;

/**
 * Puntos de experiencia (RN-44, RF-GAM-1), versión 1. Se otorgan por actividades COMPLETADAS, nunca por clics, y
 * no cambian el nivel MCER. Repetir la misma lección el mismo día da XP decreciente (20, 10, 5, 2, 2…), para no
 * premiar "farmear" en lugar de aprender.
 */
public final class XpRules {

    public static final int VERSION = 1;

    public static final int LESSON_RUN = 20;
    public static final int LESSON_RUN_MIN = 2;
    public static final int LESSON_MASTERED_BONUS = 30;
    public static final int REVIEW = 2;
    public static final int ERROR_RESOLVED = 10;
    public static final int DIAGNOSTIC = 30;
    public static final int CONVERSATION = 15;
    /** Una conversación cuenta si el profesor respondió al menos estas veces; una llamada, si duró al menos 1 min. */
    public static final int CONVERSATION_MIN_TURNS = 3;
    public static final int VOICE_MIN_SECONDS = 60;

    private XpRules() {
    }

    /** XP de la n-ésima ejecución (1, 2, 3…) de la misma lección en el mismo día. */
    public static int lessonRun(int nthToday) {
        if (nthToday <= 1) {
            return LESSON_RUN;
        }
        int shift = Math.min(nthToday - 1, 30);
        return Math.max(LESSON_RUN_MIN, LESSON_RUN >> shift);
    }
}
