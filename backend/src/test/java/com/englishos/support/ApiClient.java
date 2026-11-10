package com.englishos.support;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTP real con almacén de cookies, equivalente a un navegador: conserva la cookie de sesión y envía la
 * cabecera X-XSRF-TOKEN a partir de la cookie XSRF-TOKEN, como hará la SPA.
 */
public class ApiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;
    private boolean sendCsrfHeader = true;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public ApiClient withoutCsrfHeader() {
        this.sendCsrfHeader = false;
        return this;
    }

    public Response get(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    public Response post(String path, Object body) {
        ensureCsrfCookie();
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : JSON.writeValueAsString(body))));
    }

    public Response put(String path, Object body) {
        ensureCsrfCookie();
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    public Response patch(String path, Object body) {
        ensureCsrfCookie();
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))));
    }

    public Response login(String email, String password) {
        return post("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    private void ensureCsrfCookie() {
        if (cookie("XSRF-TOKEN").isEmpty()) {
            get("/api/v1/auth/csrf");
        }
    }

    private Response send(HttpRequest.Builder builder) {
        if (sendCsrfHeader) {
            cookie("XSRF-TOKEN").ifPresent(token -> builder.header("X-XSRF-TOKEN", token));
        }
        try {
            HttpResponse<String> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(res.statusCode(), res.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public record Response(int status, String body) {

        public JsonNode json() {
            return body == null || body.isBlank() ? JSON.createObjectNode() : JSON.readTree(body);
        }

        public String code() {
            return json().path("code").asString();
        }
    }
}
