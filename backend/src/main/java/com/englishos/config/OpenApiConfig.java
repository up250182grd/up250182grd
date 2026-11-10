package com.englishos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI; el frontend genera su cliente tipado a partir de ella. Solo visible en dev. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI englishOsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("EnglishOS API")
                .version("v1")
                .description("API de EnglishOS. Autenticación por cookie de sesión + cabecera X-XSRF-TOKEN."));
    }
}
