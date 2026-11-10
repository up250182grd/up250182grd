package com.englishos;

import java.nio.file.Files;
import java.nio.file.Path;

import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exporta la especificación OpenAPI a docs/api/openapi.json. El frontend genera su cliente tipado a partir de
 * ese archivo (npm run gen:api), de modo que un cambio de contrato en el backend se detecta al compilar el frontend.
 */
class OpenApiExportIT extends IntegrationTest {

    @Test
    void exportsOpenApiSpec() throws Exception {
        var res = newClient().get("/v3/api-docs");
        assertThat(res.status()).isEqualTo(200);

        JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        var spec = mapper.readTree(res.body());
        // Se elimina la URL del servidor (puerto aleatorio) para que el archivo sea estable.
        ((tools.jackson.databind.node.ObjectNode) spec).remove("servers");
        assertThat(spec.path("paths").has("/api/v1/auth/login")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/me")).isTrue();

        Path out = Path.of("..", "docs", "api", "openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, mapper.writeValueAsString(spec) + System.lineSeparator());
    }
}
