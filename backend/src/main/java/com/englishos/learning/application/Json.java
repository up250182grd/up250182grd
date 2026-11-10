package com.englishos.learning.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.englishos.shared.error.ApiException;

import org.springframework.stereotype.Component;

/** Lectura/escritura de las columnas JSON del contenido educativo. */
@Component
class Json {

    private final JsonMapper mapper;

    Json(JsonMapper mapper) {
        this.mapper = mapper;
    }

    JsonNode read(String json) {
        return mapper.readTree(json);
    }

    String write(JsonNode node) {
        return mapper.writeValueAsString(node);
    }

    String writeValue(Object value) {
        return mapper.writeValueAsString(value);
    }

    /** Valida que un texto recibido del administrador sea JSON del tipo esperado. */
    JsonNode parseUserJson(String raw, String field, boolean mustBeArray) {
        try {
            JsonNode node = mapper.readTree(raw == null ? "" : raw);
            if (node == null || (mustBeArray ? !node.isArray() : !node.isObject())) {
                throw ApiException.badRequest("INVALID_JSON",
                        field + " debe ser " + (mustBeArray ? "una lista" : "un objeto") + " JSON.");
            }
            return node;
        } catch (JacksonException ex) {
            throw ApiException.badRequest("INVALID_JSON", field + " no es JSON válido.");
        }
    }
}
