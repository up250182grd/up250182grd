package com.englishos.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Servidor HTTP local que imita las rutas de Mercado Pago que usa el adaptador, para probar el adaptador REAL (HTTP,
 * cabeceras, JSON) sin red. Las formas de las respuestas siguen lo supuesto en {@code MercadoPagoGateway} (🔍).
 */
public final class FakeMercadoPago implements AutoCloseable {

    public record Received(String method, String path, Map<String, List<String>> headers, String body) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpServer server;
    /** Pagos conocidos por id → JSON tal como lo devolvería la API. */
    public final Map<String, String> payments = new ConcurrentHashMap<>();
    public final List<Received> requests = new CopyOnWriteArrayList<>();
    public final AtomicBoolean failRefunds = new AtomicBoolean(false);
    private final AtomicInteger seq = new AtomicInteger(1000);

    public FakeMercadoPago() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Registra un pago con el estado, monto y referencia indicados; devuelve su id. */
    public String addPayment(String status, String amount, String currency, String externalReference) {
        String id = String.valueOf(seq.incrementAndGet());
        payments.put(id, "{\"id\":" + id + ",\"status\":\"" + status + "\",\"status_detail\":\"accredited\","
                + "\"transaction_amount\":" + amount + ",\"currency_id\":\"" + currency
                + "\",\"external_reference\":\"" + externalReference + "\"}");
        return id;
    }

    public long countRequests(String method, String pathPrefix) {
        return requests.stream().filter(r -> r.method().equals(method) && r.path().startsWith(pathPrefix)).count();
    }

    private void handle(HttpExchange ex) throws IOException {
        String body;
        try (InputStream in = ex.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getRawQuery();
        requests.add(new Received(method, path + (query == null ? "" : "?" + query), Map.copyOf(ex.getRequestHeaders()),
                body));
        if (!"Bearer TEST-token-de-prueba".equals(ex.getRequestHeaders().getFirst("Authorization"))) {
            send(ex, 401, "{\"message\":\"unauthorized\"}");
            return;
        }
        if (method.equals("POST") && path.equals("/checkout/preferences")) {
            JsonNode req = JSON.readTree(body);
            String id = "pref-" + req.path("external_reference").asString();
            send(ex, 201, "{\"id\":\"" + id + "\",\"init_point\":\"https://www.mercadopago.com.mx/checkout/v1/redirect?pref_id="
                    + id + "\",\"sandbox_init_point\":\"https://sandbox.mercadopago.com.mx/checkout/v1/redirect?pref_id="
                    + id + "\"}");
        } else if (method.equals("GET") && path.equals("/v1/payments/search")) {
            String ref = query == null ? "" : URLDecoder.decode(query.replace("external_reference=", ""),
                    StandardCharsets.UTF_8);
            List<String> found = new ArrayList<>();
            payments.values().stream().filter(p -> p.contains("\"external_reference\":\"" + ref + "\""))
                    .forEach(found::add);
            send(ex, 200, "{\"results\":[" + String.join(",", found) + "]}");
        } else if (method.equals("GET") && path.startsWith("/v1/payments/")) {
            String p = payments.get(path.substring("/v1/payments/".length()));
            send(ex, p == null ? 404 : 200, p == null ? "{\"message\":\"not found\"}" : p);
        } else if (method.equals("POST") && path.matches("/v1/payments/\\d+/refunds")) {
            if (failRefunds.get()) {
                send(ex, 400, "{\"message\":\"refund rejected\"}");
            } else {
                send(ex, 201, "{\"id\":" + seq.incrementAndGet() + ",\"status\":\"approved\"}");
            }
        } else {
            send(ex, 404, "{}");
        }
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
