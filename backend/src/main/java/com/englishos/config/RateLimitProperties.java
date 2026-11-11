package com.englishos.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites de peticiones (RNF-16). Cada regla aplica a un método + patrón de ruta y cuenta por IP (rutas públicas) o
 * por usuario (rutas con sesión). Los valores viven en configuración para ajustarlos sin desplegar código.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, Map<String, Rule> rules) {

    public enum KeyBy { IP, USER }

    public record Rule(String method, String path, int limit, Duration window, KeyBy by) {
    }

    public RateLimitProperties {
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }
}
