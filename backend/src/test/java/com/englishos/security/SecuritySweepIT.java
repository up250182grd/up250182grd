package com.englishos.security;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Barrido de seguridad (Fase 9) sobre TODAS las rutas del contrato OpenAPI vivo: una ruta nueva queda cubierta sin
 * tener que acordarse de ella. Comprueba autenticación, autorización de administración, CSRF, cabeceras, que el
 * cliente no pueda informar consumo o costos, asignación masiva, inyección y que los errores no filtren detalles.
 */
class SecuritySweepIT extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DUMMY_ID = "01ARZ3NDEKTSV4RRFFQ69G5FAV";

    /** Rutas públicas por diseño (método + ruta). */
    private static final Set<String> PUBLIC = Set.of(
            "GET /api/v1/auth/csrf",
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/verify-email",
            "POST /api/v1/auth/resend-verification",
            "POST /api/v1/auth/password-reset/request",
            "POST /api/v1/auth/password-reset/confirm",
            "POST /api/v1/webhooks/mercadopago");

    private record Op(String method, String path, String concretePath) {
    }

    private List<Op> ops;

    @BeforeEach
    void loadContract() {
        JsonNode api = JSON.readTree(newClient().get("/v3/api-docs").body());
        ops = new ArrayList<>();
        api.path("paths").properties().forEach(p -> p.getValue().properties().forEach(m -> {
            String method = m.getKey().toUpperCase();
            if (Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
                String concrete = p.getKey().replaceAll("\\{(modelId|id)}", "999999")
                        .replaceAll("\\{[^}]+}", DUMMY_ID);
                // Los ids numéricos de incidencias y modelos se resuelven arriba; el resto son ULID.
                ops.add(new Op(method, p.getKey(), concrete));
            }
        }));
        assertThat(ops).as("el contrato tiene rutas").hasSizeGreaterThan(50);
    }

    private static String body(Op op) {
        return "GET".equals(op.method()) || "DELETE".equals(op.method()) ? null : "{}";
    }

    @Test
    void everyNonPublicEndpointRequiresASession() {
        ApiClient anonymous = newClient();
        List<String> open = new ArrayList<>();
        for (Op op : ops) {
            if (PUBLIC.contains(op.method() + " " + op.path())) {
                continue;
            }
            HttpResponse<String> r = anonymous.exchange(op.method(), op.concretePath(), body(op));
            // GET sin sesión → 401. Métodos que modifican: CSRF (403) o 401; nunca éxito.
            boolean ok = "GET".equals(op.method()) ? r.statusCode() == 401 : Set.of(401, 403).contains(r.statusCode());
            if (!ok) {
                open.add(op.method() + " " + op.path() + " → " + r.statusCode());
            }
        }
        assertThat(open).as("rutas accesibles sin sesión").isEmpty();
    }

    @Test
    void studentsGet403OnEveryAdminEndpoint() {
        ApiClient student = verifiedStudent(uniqueEmail());
        List<String> leaks = new ArrayList<>();
        for (Op op : ops) {
            if (!op.path().startsWith("/api/v1/admin/")) {
                continue;
            }
            HttpResponse<String> r = student.exchange(op.method(), op.concretePath(), body(op));
            if (r.statusCode() != 403) {
                leaks.add(op.method() + " " + op.path() + " → " + r.statusCode());
            }
        }
        assertThat(leaks).as("rutas de administración accesibles para un alumno").isEmpty();
    }

    @Test
    void everyStateChangingEndpointRequiresTheCsrfToken() {
        ApiClient student = verifiedStudent(uniqueEmail()).withoutCsrfHeader();
        List<String> missing = new ArrayList<>();
        for (Op op : ops) {
            if ("GET".equals(op.method()) || op.path().equals("/api/v1/webhooks/mercadopago")) {
                continue;
            }
            HttpResponse<String> r = student.exchange(op.method(), op.concretePath(), body(op));
            if (r.statusCode() != 403 || !r.body().contains("CSRF_INVALID")) {
                missing.add(op.method() + " " + op.path() + " → " + r.statusCode());
            }
        }
        assertThat(missing).as("rutas que aceptan cambios sin token CSRF").isEmpty();
    }

    @Test
    void theClientCannotReportConsumptionOrPrices() {
        // Ninguna petición de alumno lleva campos de consumo o costos: los mide y calcula el servidor (ADR-0004).
        JsonNode api = JSON.readTree(newClient().get("/v3/api-docs").body());
        JsonNode schemas = api.path("components").path("schemas");
        // "token" a secas es el token de un solo uso del correo (verificación/restablecimiento), no consumo de IA.
        Pattern suspicious = Pattern.compile("(?i)usage|units|.+tokens?$|tokens$|cost|price|total|factor|markup|tax|credited");
        List<String> found = new ArrayList<>();
        api.path("paths").properties().forEach(p -> p.getValue().properties().forEach(m -> {
            if (p.getKey().startsWith("/api/v1/admin/")) {
                return;
            }
            JsonNode ref = m.getValue().path("requestBody").path("content").path("application/json").path("schema")
                    .path("$ref");
            if (ref.isMissingNode()) {
                return;
            }
            String name = ref.asString().substring(ref.asString().lastIndexOf('/') + 1);
            schemas.path(name).path("properties").properties().forEach(prop -> {
                if (suspicious.matcher(prop.getKey()).find()) {
                    found.add(m.getKey().toUpperCase() + " " + p.getKey() + " → " + name + "." + prop.getKey());
                }
            });
        }));
        assertThat(found).as("campos de consumo/costo aceptados del cliente").isEmpty();
    }

    @Test
    void apiResponsesCarrySecurityHeadersAndNoSensitiveData() {
        ApiClient student = verifiedStudent(uniqueEmail());
        HttpResponse<String> me = student.exchange("GET", "/api/v1/me", null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(me.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(me.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(
                v -> assertThat(v).contains("default-src 'none'"));
        assertThat(me.headers().firstValue("Cache-Control")).hasValueSatisfying(v -> assertThat(v).contains("no-store"));
        assertThat(me.body().toLowerCase()).doesNotContain("password", "hash", "token");

        HttpResponse<String> users = admin().exchange("GET", "/api/v1/admin/users", null);
        assertThat(users.body().toLowerCase()).doesNotContain("password", "token_hash");

        // La cookie de sesión no es legible desde JavaScript y no viaja en peticiones de otros sitios.
        ApiClient c = newClient();
        String email = uniqueEmail();
        register(c, email, STRONG_PASSWORD);
        HttpResponse<String> login = c.exchange("POST", "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + STRONG_PASSWORD + "\"}");
        String setCookie = String.join("; ", login.headers().allValues("Set-Cookie"));
        assertThat(setCookie).contains("SESSION=").containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Lax");
    }

    @Test
    void errorsDoNotLeakInternalsAndActuatorIsClosed() {
        ApiClient student = verifiedStudent(uniqueEmail());
        HttpResponse<String> malformed = student.exchange("PATCH", "/api/v1/me/profile", "{\"displayName\": ");
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(malformed.body()).doesNotContain("Exception", "at com.", "at org.", "jackson", "SQL");

        HttpResponse<String> typeError = student.exchange("GET", "/api/v1/vocabulary/queue?x=" + "%27", null);
        assertThat(typeError.body()).doesNotContain("Exception", "at com.");

        for (String path : List.of("/actuator/env", "/actuator/configprops", "/actuator/heapdump", "/actuator/beans",
                "/actuator/loggers", "/actuator/mappings")) {
            int code = newClient().exchange("GET", path, null).statusCode();
            assertThat(code).as(path).isIn(401, 403, 404);
        }
        HttpResponse<String> health = newClient().exchange("GET", "/actuator/health", null);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).doesNotContain("db", "diskSpace", "mysql");
    }

    @Test
    void massAssignmentIsIgnoredAndRolesCannotBeSelfAssigned() {
        ApiClient c = newClient();
        String email = uniqueEmail();
        var reg = c.exchange("POST", "/api/v1/auth/register", JSON.writeValueAsString(Map.of(
                "email", email, "password", STRONG_PASSWORD, "displayName", "Mallory", "acceptTerms", true,
                "acceptPrivacy", true, "roles", List.of("ADMIN"), "status", "ACTIVE", "emailVerified", true)));
        assertThat(reg.statusCode()).as("los campos extra se ignoran, el registro procede").isEqualTo(201);
        c.login(email, STRONG_PASSWORD);
        JsonNode me = c.get("/api/v1/me").json();
        assertThat(me.path("roles").toString()).doesNotContain("ADMIN");
        assertThat(me.path("emailVerified").asBoolean()).isFalse();
        var patch = c.patch("/api/v1/me/profile", Map.of("displayName", "Mallory", "roles", List.of("ADMIN")));
        assertThat(patch.status()).isEqualTo(200);
        assertThat(c.get("/api/v1/me").json().path("roles").toString()).doesNotContain("ADMIN");
        assertThat(c.get("/api/v1/admin/users").status()).isEqualTo(403);
    }

    @Test
    void injectionAttemptsAreTreatedAsData() {
        ApiClient admin = admin();
        var search = admin.get("/api/v1/admin/users?q=" + java.net.URLEncoder.encode("' OR 1=1 -- ",
                java.nio.charset.StandardCharsets.UTF_8));
        assertThat(search.status()).isEqualTo(200);
        assertThat(search.json().path("items").size()).isZero();
        var login = newClient().login("' or '1'='1", "' or '1'='1");
        assertThat(login.status()).isIn(400, 401);

        // XSS almacenado: el texto se guarda tal cual y se devuelve como JSON (React lo escapa al pintarlo).
        ApiClient s = verifiedStudent(uniqueEmail());
        String xss = "<img src=x onerror=alert(1)>";
        assertThat(s.patch("/api/v1/me/profile", Map.of("displayName", xss)).status()).isEqualTo(200);
        HttpResponse<String> me = s.exchange("GET", "/api/v1/me", null);
        assertThat(me.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("application/json"));
    }

    @Test
    void manipulatedAmountsAreRejected() {
        ApiClient s = verifiedStudent(uniqueEmail());
        for (Object amount : List.of(-100, 0, new BigDecimal("100.001"), new BigDecimal("1e9"), "cien")) {
            var r = s.exchange("POST", "/api/v1/me/wallet/simulated-topups", JSON.writeValueAsString(
                    Map.of("amount", amount, "idempotencyKey", UUID.randomUUID().toString())));
            assertThat(r.statusCode()).as("monto " + amount).isEqualTo(400);
        }
        assertThat(s.get("/api/v1/me/wallet").json().path("available").decimalValue()).isEqualByComparingTo("0");
        var limits = s.exchange("PUT", "/api/v1/me/wallet/preferences",
                "{\"monthlyLimit\": -1, \"lowBalanceThreshold\": 10}");
        assertThat(limits.statusCode()).isEqualTo(400);
    }
}
