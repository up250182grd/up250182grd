package com.englishos.learning.api;

/** Texto pedagógico bilingüe. El alumno puede alternar entre español e inglés en cualquier momento. */
public record Localized(String es, String en) {

    public Localized {
        if (es == null || es.isBlank() || en == null || en.isBlank()) {
            throw new IllegalArgumentException("El texto debe existir en español y en inglés");
        }
    }

    /** Versión que admite ausencia (pistas opcionales). */
    public static Localized ofNullable(String es, String en) {
        return es == null || en == null || es.isBlank() || en.isBlank() ? null : new Localized(es, en);
    }
}
