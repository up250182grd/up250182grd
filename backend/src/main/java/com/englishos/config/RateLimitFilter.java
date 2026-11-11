package com.englishos.config;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.englishos.identity.api.CurrentUser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Límite de peticiones con ventana fija por regla y clave (IP o usuario). Responde 429 con {@code Retry-After}.
 *
 * <p>Los contadores viven en memoria: válido para UNA instancia del backend (el despliegue del MVP). Con varias
 * instancias se debe mover a un almacén compartido (p. ej. Redis) o limitar en el proxy. La IP es la que entrega el
 * contenedor web; detrás del proxy se toma de {@code X-Forwarded-For} solo si el proxy es de confianza
 * ({@code server.forward-headers-strategy}).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private record Compiled(String name, String method, String path, int limit, long windowMillis,
            RateLimitProperties.KeyBy by) {
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }

    private final List<Compiled> rules;
    private final boolean enabled;
    private final JsonMapper json;
    private final Clock clock;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong();

    public RateLimitFilter(RateLimitProperties props, JsonMapper json, Clock clock) {
        this.enabled = props.enabled();
        this.rules = props.rules().entrySet().stream()
                .map(e -> new Compiled(e.getKey(), e.getValue().method(), e.getValue().path(), e.getValue().limit(),
                        e.getValue().window().toMillis(), e.getValue().by()))
                .toList();
        this.json = json;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(req, res);
            return;
        }
        long now = clock.millis();
        cleanup(now);
        String path = req.getRequestURI().substring(req.getContextPath().length());
        for (Compiled r : rules) {
            if (!r.method().equalsIgnoreCase(req.getMethod()) || !matcher.match(r.path(), path)) {
                continue;
            }
            String key = key(r, req);
            long windowStart = now - (now % r.windowMillis());
            Window w = windows.compute(r.name() + "|" + key, (k, old) -> old == null || old.start != windowStart
                    ? new Window(windowStart) : old);
            if (w.count.incrementAndGet() > r.limit()) {
                long retry = Math.max(1, (windowStart + r.windowMillis() - now + 999) / 1000);
                log.warn("Límite de peticiones '{}' alcanzado", r.name()); // sin IP ni usuario en el log (RNF-12)
                reject(res, retry);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private String key(Compiled r, HttpServletRequest req) {
        if (r.by() == RateLimitProperties.KeyBy.USER) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CurrentUser u) {
                return "u:" + u.id();
            }
        }
        return "ip:" + req.getRemoteAddr();
    }

    private void reject(HttpServletResponse res, long retryAfterSeconds) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Demasiadas solicitudes. Espera " + retryAfterSeconds + " s e inténtalo de nuevo.");
        pd.setProperty("code", "RATE_LIMITED");
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(res.getOutputStream(), pd);
    }

    /** Elimina ventanas vencidas como máximo una vez por minuto (evita crecer sin límite). */
    private void cleanup(long now) {
        long last = lastCleanup.get();
        if (now - last < 60_000 || !lastCleanup.compareAndSet(last, now)) {
            return;
        }
        long maxWindow = rules.stream().mapToLong(Compiled::windowMillis).max().orElse(60_000);
        windows.entrySet().removeIf(e -> now - e.getValue().start > maxWindow * 2);
    }
}
