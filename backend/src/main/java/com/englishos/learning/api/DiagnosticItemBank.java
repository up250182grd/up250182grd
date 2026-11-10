package com.englishos.learning.api;

import java.util.List;

import tools.jackson.databind.JsonNode;

/** Banco de ítems del diagnóstico. Lo usa el módulo {@code assessment}; las soluciones nunca salen de aquí. */
public interface DiagnosticItemBank {

    record ItemRef(Long id, Skill skill, CefrLevel level) {
    }

    /** Vista segura para el alumno (sin solución). */
    record ItemView(String publicId, String type, Skill skill, CefrLevel level, Localized instructions,
            JsonNode prompt) {
    }

    List<ItemRef> publishedItems();

    ItemView view(Long itemId);

    /**
     * @return si la respuesta es correcta.
     * @throws com.englishos.shared.error.ApiException 400 si la respuesta no tiene la forma esperada.
     */
    boolean isCorrect(Long itemId, JsonNode answer);
}
