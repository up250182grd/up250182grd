package com.englishos.config;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad HTTP (ADR-0002): sesión de servidor con cookie HttpOnly, CSRF por cookie legible + cabecera
 * {@code X-XSRF-TOKEN} para la SPA, autorización por rol en URL y por permiso en métodos (@PreAuthorize).
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    public static final String SESSION_COOKIE = "ENGLISHOS_SESSION";

    /** Única ruta pública que acepta POST sin sesión ni CSRF: la valida la firma de la pasarela. */
    static final String PAYMENT_WEBHOOK = "/api/v1/webhooks/mercadopago";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository, JsonMapper jsonMapper,
            @Value("${springdoc.api-docs.enabled:false}") boolean openApiEnabled) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // Las notificaciones de la pasarela no vienen de un navegador: se autentican por firma HMAC.
                        .ignoringRequestMatchers(PAYMENT_WEBHOOK))
                .cors(cors -> {
                })
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(rc -> rc.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies(SESSION_COOKIE)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(f -> f.deny()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                            .requestMatchers(HttpMethod.POST,
                                    "/api/v1/auth/register",
                                    "/api/v1/auth/login",
                                    "/api/v1/auth/verify-email",
                                    "/api/v1/auth/resend-verification",
                                    "/api/v1/auth/password-reset/request",
                                    "/api/v1/auth/password-reset/confirm").permitAll()
                            .requestMatchers(HttpMethod.POST, PAYMENT_WEBHOOK).permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                            .requestMatchers("/error").permitAll();
                    if (openApiEnabled) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    auth.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().denyAll();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> writeProblem(jsonMapper, res,
                                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Inicia sesión para continuar."))
                        .accessDeniedHandler((req, res, e) -> {
                            if (e instanceof CsrfException) {
                                writeProblem(jsonMapper, res, HttpStatus.FORBIDDEN, "CSRF_INVALID",
                                        "La solicitud no pudo verificarse. Recarga la página e inténtalo de nuevo.");
                            } else {
                                writeProblem(jsonMapper, res, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                        "No tienes permiso para esta acción.");
                            }
                        }));
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookies) {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookiePath("/");
        repo.setCookieCustomizer(c -> c.sameSite("Lax").secure(secureCookies));
        return repo;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /** No se usa autenticación por UserDetailsService; este bean evita el usuario en memoria por defecto. */
    @Bean
    UserDetailsService noUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("No soportado");
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.corsAllowedOrigins();
        if (!origins.isEmpty()) {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOrigins(origins);
            cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
            cfg.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key"));
            cfg.setAllowCredentials(true);
            cfg.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", cfg);
        }
        return source;
    }

    private static void writeProblem(JsonMapper jsonMapper, HttpServletResponse res, HttpStatus status, String code,
            String detail) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", code);
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(res.getOutputStream(), pd);
    }

    /**
     * Manejo de CSRF para SPA (patrón documentado por Spring Security): la cabecera trae el valor crudo de la
     * cookie, mientras que los formularios renderizados por servidor usarían el valor con XOR (protección BREACH).
     * Además fuerza la escritura de la cookie XSRF-TOKEN en cada respuesta.
     */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            xor.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String header = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(header) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
