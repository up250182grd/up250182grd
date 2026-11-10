package com.englishos.learning.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalización de respuestas escritas para comparar sin penalizar diferencias irrelevantes: mayúsculas, espacios
 * repetidos, apóstrofos y comillas tipográficas (los de teclados móviles) y puntuación final.
 * Las variantes válidas con contenido distinto (p. ej. "isn't" / "is not") se listan explícitamente en la solución.
 */
public final class AnswerNormalizer {

    private AnswerNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'').replace('`', '\'')
                .replace('“', '"').replace('”', '"')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        while (!s.isEmpty() && ".!?".indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }
}
