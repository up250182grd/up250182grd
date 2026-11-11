package com.englishos.tutor.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Formato de respuesta del profesor: texto para el alumno, luego {@value #MARKER} y un arreglo JSON de correcciones.
 * Las correcciones se validan como datos (no se ejecuta nada de lo que diga el modelo) y se descartan si no tienen
 * la forma esperada.
 */
public final class ReplyFormat {

    public static final String MARKER = "<<<CORRECTIONS>>>";
    private static final Set<String> CATEGORIES = Set.of("GRAMMAR", "VOCABULARY", "SPELLING", "WORD_ORDER", "OTHER");
    private static final int MAX_CORRECTIONS = 10;

    private ReplyFormat() {
    }

    public record Correction(String original, String correction, String explanationEs, String explanationEn,
            String category) {
    }

    public record Parsed(String visibleText, List<Correction> corrections) {
    }

    public static Parsed parse(String full, JsonMapper json) {
        int idx = full.indexOf(MARKER);
        if (idx < 0) {
            return new Parsed(full.trim(), List.of());
        }
        String visible = full.substring(0, idx).trim();
        String tail = full.substring(idx + MARKER.length()).trim();
        List<Correction> out = new ArrayList<>();
        try {
            JsonNode arr = json.readTree(tail);
            if (arr != null && arr.isArray()) {
                for (JsonNode c : arr) {
                    if (out.size() >= MAX_CORRECTIONS) {
                        break;
                    }
                    String original = text(c, "original", 500);
                    String correction = text(c, "correction", 500);
                    if (original.isBlank() || correction.isBlank() || original.equals(correction)) {
                        continue;
                    }
                    String category = text(c, "category", 40).toUpperCase(java.util.Locale.ROOT);
                    out.add(new Correction(original, correction, text(c, "explanation_es", 1000),
                            text(c, "explanation_en", 1000), CATEGORIES.contains(category) ? category : "OTHER"));
                }
            }
        } catch (JacksonException ex) {
            // Correcciones mal formadas: se ignoran; la respuesta visible sigue siendo válida.
        }
        return new Parsed(visible, out);
    }

    private static String text(JsonNode node, String field, int max) {
        String v = node.path(field).isString() ? node.path(field).asString().trim() : "";
        return v.length() > max ? v.substring(0, max) : v;
    }

    /**
     * Filtro de streaming: deja pasar el texto visible y retiene lo justo para no enviar al alumno el marcador ni
     * el JSON de correcciones, aunque el marcador llegue partido entre fragmentos.
     */
    public static final class StreamFilter {

        private final StringBuilder pending = new StringBuilder();
        private boolean markerSeen;

        /** @return texto que ya se puede mostrar (posiblemente vacío). */
        public String accept(String delta) {
            if (markerSeen) {
                return "";
            }
            pending.append(delta);
            int idx = pending.indexOf(MARKER);
            if (idx >= 0) {
                markerSeen = true;
                String out = pending.substring(0, idx);
                pending.setLength(0);
                return out;
            }
            int safe = pending.length() - longestMarkerPrefixSuffix();
            String out = pending.substring(0, safe);
            pending.delete(0, safe);
            return out;
        }

        /** Al terminar el flujo: lo retenido que no resultó ser el marcador. */
        public String flush() {
            String out = markerSeen ? "" : pending.toString();
            pending.setLength(0);
            return out;
        }

        private int longestMarkerPrefixSuffix() {
            for (int len = Math.min(MARKER.length() - 1, pending.length()); len > 0; len--) {
                if (MARKER.startsWith(pending.substring(pending.length() - len))) {
                    return len;
                }
            }
            return 0;
        }
    }
}
