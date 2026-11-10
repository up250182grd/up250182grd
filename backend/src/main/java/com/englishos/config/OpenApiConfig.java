package com.englishos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ObjectSchema;

import org.springdoc.core.utils.SpringDocUtils;
import tools.jackson.databind.JsonNode;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI; el frontend genera su cliente tipado a partir de ella. Solo visible en dev. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static {
        // Los enunciados/soluciones de ejercicios son JSON libre: se documentan como objeto, no como la clase de Jackson.
        SpringDocUtils.getConfig().replaceWithSchema(JsonNode.class, new ObjectSchema());
    }

    @Bean
    OpenAPI englishOsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("EnglishOS API")
                .version("v1")
                .description("API de EnglishOS. Autenticación por cookie de sesión + cabecera X-XSRF-TOKEN."));
    }
}
