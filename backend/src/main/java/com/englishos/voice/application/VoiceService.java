package com.englishos.voice.application;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import com.englishos.ai.api.RealtimeVoiceProvider;
import com.englishos.assessment.api.LearnerLevels;
import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.api.LearnerProfiles;
import com.englishos.metering.api.MeteringApi;
import com.englishos.metering.api.MeteringApi.CostEstimate;
import com.englishos.metering.api.MeteringApi.SettlementView;
import com.englishos.shared.error.ApiException;
import com.englishos.tutor.api.LearnerErrorsApi;
import com.englishos.voice.domain.VoiceSession;
import com.englishos.voice.domain.VoiceTranscript;
import com.englishos.voice.infrastructure.VoiceSessionRepository;
import com.englishos.voice.infrastructure.VoiceTranscriptRepository;
import com.englishos.wallet.api.WalletApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Llamadas de voz en tiempo real (RF-VOC, flujo §7.4). El audio va directo del navegador al proveedor; el servidor
 * crea la llamada con su clave, abre un canal lateral y recibe por él el consumo (RN-34). El cobro se liquida una vez
 * al terminar con la tarifa y el factor fijados al iniciar.
 *
 * <p>Cortes automáticos: consumo medido ≥ 90 % de la reserva sin saldo para ampliarla (RN-23), duración máxima,
 * cliente sin latido (desconexión) y cierre del canal por el proveedor. Al reiniciar el servidor, las llamadas que
 * quedaron abiertas se liquidan con el consumo registrado.
 */
@Service
public class VoiceService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);
    public static final String SUBJECT = "VOICE_SESSION";
    static final String PROMPT_VERSION = "voice-v1";

    private final VoiceSessionRepository sessions;
    private final VoiceTranscriptRepository transcripts;
    private final MeteringApi metering;
    private final WalletApi wallet;
    private final LearnerProfiles profiles;
    private final LearnerLevels levels;
    private final LearnerErrorsApi learnerErrors;
    private final RealtimeVoiceProvider provider;
    private final VoiceEventHub hub;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final String modelCode;
    private final String voice;
    private final int maxMinutes;
    private final int minMinutes;
    private final int maxOutputTokens;
    private final BigDecimal reserveMargin;
    private final BigDecimal cutRatio;
    private final Duration heartbeatTimeout;
    private final Duration endGrace;
    private final Duration warnBefore;
    private final Duration watchdogPeriod;
    private final BigDecimal adminMonthlyCap;
    private final Map<String, Long> perMinuteUnits;
    private final String promptTemplate;
    private final org.springframework.context.ApplicationEventPublisher events;

    private final Map<Long, RealtimeVoiceProvider.Sideband> sidebands = new ConcurrentHashMap<>();
    private final Set<Long> warned = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2,
            Thread.ofVirtual().name("voice-", 0).factory());

    VoiceService(VoiceSessionRepository sessions, VoiceTranscriptRepository transcripts, MeteringApi metering,
            WalletApi wallet, LearnerProfiles profiles, LearnerLevels levels, LearnerErrorsApi learnerErrors,
            RealtimeVoiceProvider provider, VoiceEventHub hub, Clock clock, TransactionTemplate tx,
            org.springframework.context.ApplicationEventPublisher events,
            @Value("${app.ai.realtime-model:fake-voice-1}") String modelCode,
            @Value("${app.ai.realtime-voice:alloy}") String voice,
            @Value("${app.voice.max-minutes:15}") int maxMinutes,
            @Value("${app.voice.min-minutes:2}") int minMinutes,
            @Value("${app.voice.max-output-tokens:400}") int maxOutputTokens,
            @Value("${app.voice.reserve-margin:1.20}") BigDecimal reserveMargin,
            @Value("${app.voice.cut-ratio:0.90}") BigDecimal cutRatio,
            @Value("${app.voice.heartbeat-timeout:30s}") Duration heartbeatTimeout,
            @Value("${app.voice.end-grace:2s}") Duration endGrace,
            @Value("${app.voice.warn-before:60s}") Duration warnBefore,
            @Value("${app.voice.watchdog-period:2s}") Duration watchdogPeriod,
            @Value("${app.billing.admin-monthly-cap-mxn:1000.00}") BigDecimal adminMonthlyCap,
            @Value("${app.voice.estimate.text-in-per-minute:1500}") long textInPerMinute,
            @Value("${app.voice.estimate.audio-in-per-minute:600}") long audioInPerMinute,
            @Value("${app.voice.estimate.audio-out-per-minute:900}") long audioOutPerMinute,
            @Value("${app.voice.estimate.text-out-per-minute:200}") long textOutPerMinute) throws IOException {
        this.sessions = sessions;
        this.transcripts = transcripts;
        this.metering = metering;
        this.wallet = wallet;
        this.profiles = profiles;
        this.levels = levels;
        this.learnerErrors = learnerErrors;
        this.provider = provider;
        this.hub = hub;
        this.clock = clock;
        this.tx = tx;
        this.events = events;
        this.modelCode = modelCode;
        this.voice = voice;
        this.maxMinutes = maxMinutes;
        this.minMinutes = minMinutes;
        this.maxOutputTokens = maxOutputTokens;
        this.reserveMargin = reserveMargin;
        this.cutRatio = cutRatio;
        this.heartbeatTimeout = heartbeatTimeout;
        this.endGrace = endGrace;
        this.warnBefore = warnBefore;
        this.watchdogPeriod = watchdogPeriod;
        this.adminMonthlyCap = adminMonthlyCap;
        Map<String, Long> units = new LinkedHashMap<>();
        units.put("text_in", textInPerMinute);
        units.put("audio_in", audioInPerMinute);
        units.put("audio_out", audioOutPerMinute);
        units.put("text_out", textOutPerMinute);
        this.perMinuteUnits = Map.copyOf(units);
        try (InputStream in = new ClassPathResource("prompts/" + PROMPT_VERSION + ".txt").getInputStream()) {
            this.promptTemplate = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---- Vistas -------------------------------------------------------------------------------

    public record VoiceModelInfo(String provider, String modelCode, String displayName, boolean simulated) {
    }

    /** RF-VOC-1: lo que el alumno debe saber ANTES de llamar. Los costos por minuto son estimaciones. */
    public record VoiceQuote(VoiceModelInfo model, BigDecimal estimatedPerMinuteMxn, int maxMinutes, BigDecimal availableMxn,
            BigDecimal spentThisMonthMxn, BigDecimal monthlyLimitMxn, boolean canStart, String reason, boolean admin) {
    }

    public record VoiceStartResult(String sessionId, boolean simulated, String sdpAnswer, int maxDurationSeconds,
            BigDecimal reservedMxn, VoiceModelInfo model) {
    }

    public record VoiceTranscriptLine(String role, String content, Instant at) {
    }

    public record VoiceMeterEvent(long elapsedSeconds, int maxDurationSeconds, CostEstimate measured, BigDecimal reservedMxn) {
    }

    public record VoiceSessionView(String id, String mode, String topic, String status, String endReason, VoiceModelInfo model,
            int maxDurationSeconds, long connectedSeconds, BigDecimal reservedMxn, CostEstimate measured,
            boolean transcriptStored, List<VoiceTranscriptLine> transcript, int studentTurns, SettlementView settlement,
            Instant createdAt, Instant connectedAt, Instant endedAt) {
    }

    public record VoiceSessionSummary(String id, String mode, String topic, String status, long connectedSeconds,
            Instant createdAt) {
    }

    // ---- Ciclo de vida -------------------------------------------------------------------------

    @PostConstruct
    void startWatchdog() {
        scheduler.scheduleWithFixedDelay(this::watchdog, watchdogPeriod.toMillis(), watchdogPeriod.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        sidebands.values().forEach(s -> {
            try {
                s.close();
            } catch (RuntimeException ignored) {
                // cierre de mejor esfuerzo
            }
        });
    }

    /** Recuperación al arrancar: liquida con lo registrado las llamadas que quedaron abiertas. */
    @Override
    public void run(ApplicationArguments args) {
        for (VoiceSession s : sessions.findByStatusIn(
                List.of(VoiceSession.Status.STARTING, VoiceSession.Status.ACTIVE, VoiceSession.Status.ENDING))) {
            tx.executeWithoutResult(st -> sessions.findByIdForUpdate(s.getId()).ifPresent(v -> v.beginEnding("SERVER_RESTART")));
            if (s.getProviderCallId() != null) {
                safeHangup(s.getProviderCallId());
            }
            finalizeSession(s.getId());
        }
    }

    // ---- Casos de uso --------------------------------------------------------------------------

    public VoiceQuote quote(CurrentUser user) {
        MeteringApi.SessionPricing pricing = metering.pricingFor(user.id(), user.isAdmin(), provider.provider(), modelCode);
        CostEstimate perMinute = metering.estimate(pricing.pricingVersionId(), pricing.policyId(), perMinuteUnits);
        VoiceModelInfo model = modelInfo(pricing.modelId());
        BigDecimal available = wallet.available(user.id());
        BigDecimal spent = wallet.capturedThisMonth(user.id(), profiles.zoneOf(user.id()));
        BigDecimal limit = wallet.monthlyLimit(user.id()).orElse(null);
        if (!profiles.isEmailVerified(user.id())) {
            return new VoiceQuote(model, perMinute.totalMxn(), 0, available, spent, limit, false,
                    "Confirma tu correo para usar la voz.", user.isAdmin());
        }
        if (user.isAdmin()) {
            return new VoiceQuote(model, perMinute.totalMxn(), maxMinutes, available, spent, limit, true, null, true);
        }
        if (!perMinute.available()) {
            return new VoiceQuote(model, null, 0, available, spent, limit, false,
                    "No es posible estimar el costo ahora (falta el tipo de cambio).", false);
        }
        int minutes = affordableMinutes(available, spent, limit, perMinute.totalMxn());
        boolean ok = minutes >= minMinutes;
        return new VoiceQuote(model, perMinute.totalMxn(), minutes, available, spent, limit, ok,
                ok ? null : "Saldo o límite mensual insuficientes para al menos " + minMinutes + " minutos.", false);
    }

    public VoiceStartResult start(CurrentUser user, VoiceSession.Mode mode, String topic, String sdpOffer) {
        if (!profiles.isEmailVerified(user.id())) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Confirma tu correo para usar la voz.");
        }
        if (!provider.simulated() && (sdpOffer == null || sdpOffer.isBlank())) {
            throw ApiException.badRequest("SDP_REQUIRED", "Falta la oferta de conexión de audio del navegador.");
        }
        String cleanTopic = topic == null || topic.isBlank() ? null : topic.strip();
        if (cleanTopic != null && cleanTopic.length() > 200) {
            throw ApiException.badRequest("TOPIC_TOO_LONG", "El tema admite hasta 200 caracteres.");
        }
        if (sessions.existsByActiveUserId(user.id())) {
            throw ApiException.conflict("CALL_IN_PROGRESS", "Ya tienes una llamada activa. Termínala antes de iniciar otra.");
        }

        // 1) Reserva y registro de la sesión (transacción corta).
        VoiceSession session = tx.execute(st -> reserveAndCreate(user, mode, cleanTopic));
        String instructions = instructions(user.id(), mode, cleanTopic);
        VoiceModelInfo model = modelInfo(session.getModelId());

        // 2) Llamada en el proveedor y canal lateral (sin transacción abierta).
        RealtimeVoiceProvider.CallStarted call;
        try {
            call = provider.startCall(new RealtimeVoiceProvider.CallRequest(model.modelCode(), instructions, voice,
                    maxOutputTokens, sdpOffer));
            sidebands.put(session.getId(), provider.openSideband(call.callId(), listenerFor(session.getId())));
        } catch (RuntimeException ex) {
            log.warn("No se pudo iniciar la llamada de voz {}: {}", session.getPublicId(), ex.getMessage());
            failAndSettle(session.getId(), "PROVIDER_ERROR");
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_PROVIDER_ERROR",
                    "No se pudo conectar con el profesor de voz. No se cobró nada; inténtalo de nuevo.");
        }

        // 3) Activa.
        VoiceSession active = tx.execute(st -> {
            VoiceSession s = sessions.findByIdForUpdate(session.getId()).orElseThrow();
            s.connected(call.callId(), wallet.reservedFor(SUBJECT, s.getId()), Instant.now(clock));
            return s;
        });
        return new VoiceStartResult(active.getPublicId(), provider.simulated(), call.sdpAnswer(),
                active.getMaxDurationSeconds(), active.getReservedMxn(), model);
    }

    private VoiceSession reserveAndCreate(CurrentUser user, VoiceSession.Mode mode, String topic) {
        MeteringApi.SessionPricing pricing = metering.pricingFor(user.id(), user.isAdmin(), provider.provider(), modelCode);
        CostEstimate perMinute = metering.estimate(pricing.pricingVersionId(), pricing.policyId(), perMinuteUnits);
        Instant now = Instant.now(clock);
        int minutes;
        BigDecimal target = BigDecimal.ZERO;
        if (user.isAdmin()) {
            BigDecimal spent = metering.settledThisMonth(user.id(), profiles.zoneOf(user.id()));
            if (perMinute.available() && spent.add(perMinute.totalMxn().multiply(BigDecimal.valueOf(minMinutes)))
                    .compareTo(adminMonthlyCap) > 0) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "ADMIN_MONTHLY_CAP_REACHED",
                        "Se alcanzó el tope mensual de consumo interno del administrador.");
            }
            minutes = maxMinutes;
        } else {
            if (!perMinute.available()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRICING_UNAVAILABLE",
                        "No es posible calcular el costo en este momento. Intenta más tarde.");
            }
            BigDecimal available = wallet.available(user.id());
            BigDecimal spent = wallet.capturedThisMonth(user.id(), profiles.zoneOf(user.id()));
            BigDecimal limit = wallet.monthlyLimit(user.id()).orElse(null);
            minutes = affordableMinutes(available, spent, limit, perMinute.totalMxn());
            if (minutes < minMinutes && affordableMinutes(available, spent, null, perMinute.totalMxn()) >= minMinutes) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "MONTHLY_LIMIT_REACHED",
                        "Tu límite mensual no alcanza para al menos " + minMinutes + " minutos de llamada.");
            }
            if (minutes < minMinutes) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_FUNDS",
                        "Necesitas saldo para al menos " + minMinutes + " minutos de llamada. Recarga tu billetera.");
            }
            target = reserveFor(perMinute.totalMxn(), minutes);
        }
        VoiceSession s;
        try {
            s = sessions.saveAndFlush(VoiceSession.start(user.id(), mode, topic, pricing.modelId(),
                    pricing.pricingVersionId(), pricing.policyId(), provider.provider(), minutes * 60,
                    profiles.storeTranscripts(user.id()), now));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("CALL_IN_PROGRESS", "Ya tienes una llamada activa.");
        }
        if (target.signum() > 0) {
            wallet.ensureReserved(user.id(), SUBJECT, s.getId(), target); // RN-22
        }
        return s;
    }

    public void heartbeat(CurrentUser user, String sessionId) {
        tx.executeWithoutResult(st -> {
            VoiceSession s = owned(user, sessionId);
            if (s.isLive()) {
                sessions.findByIdForUpdate(s.getId()).ifPresent(v -> v.heartbeat(Instant.now(clock)));
            }
        });
    }

    public VoiceSessionView end(CurrentUser user, String sessionId) {
        VoiceSession s = tx.execute(st -> owned(user, sessionId));
        requestEnd(s.getId(), "USER");
        return view(user, sessionId);
    }

    public VoiceSessionView view(CurrentUser user, String sessionId) {
        return tx.execute(st -> toView(owned(user, sessionId)));
    }

    public List<VoiceSessionSummary> list(CurrentUser user) {
        Instant now = Instant.now(clock);
        return tx.execute(st -> sessions.findRecentByUser(user.id(), PageRequest.of(0, 20)).stream()
                .map(s -> new VoiceSessionSummary(s.getPublicId(), s.getMode().name(), s.getTopic(), s.getStatus().name(),
                        s.connectedSeconds(now), s.getCreatedAt()))
                .toList());
    }

    /** Suscribe a la interfaz a los eventos en vivo de la llamada; envía de inmediato el estado actual. */
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribe(CurrentUser user, String sessionId) {
        VoiceSession s = tx.execute(st -> owned(user, sessionId));
        var emitter = hub.subscribe(s.getId(), (s.getMaxDurationSeconds() + 300) * 1000L);
        hub.send(emitter, "meter", meter(s.getId()));
        if (!s.isLive()) {
            hub.send(emitter, "ended", Map.of("reason", String.valueOf(s.getEndReason())));
        }
        return emitter;
    }

    // ---- Canal lateral --------------------------------------------------------------------------

    private RealtimeVoiceProvider.Listener listenerFor(Long sessionId) {
        return new RealtimeVoiceProvider.Listener() {
            @Override
            public void onUsage(String responseId, Map<String, Long> units) {
                handleUsage(sessionId, responseId, units);
            }

            @Override
            public void onTranscript(String role, String text) {
                handleTranscript(sessionId, role, text);
            }

            @Override
            public void onClosed(String reason) {
                requestEnd(sessionId, "PROVIDER_" + reason);
            }
        };
    }

    void handleUsage(Long sessionId, String responseId, Map<String, Long> units) {
        try {
            VoiceSession s = tx.execute(st -> {
                VoiceSession v = sessions.findByIdForUpdate(sessionId).orElseThrow();
                // El consumo se registra siempre que el proveedor lo informe: ocurrió (RN-34).
                metering.recordUsage(new MeteringApi.UsageInput(v.getUserId(), SUBJECT, v.getId(), v.getModelId(),
                        v.getPricingVersionId(), responseId, units, provider.simulated() ? "SIMULATED" : "PROVIDER_EVENT"));
                v.usageReceived(Instant.now(clock));
                if (v.getStatus() == VoiceSession.Status.ENDED && metering.settlementOf(SUBJECT, v.getId()).isPresent()) {
                    wallet.openIncident("USAGE_AFTER_SETTLEMENT", v.getUserId(), SUBJECT, String.valueOf(v.getId()),
                            "Llegó consumo de la llamada después de liquidarla; requiere conciliación.");
                }
                return v;
            });
            enforceReserve(s);
            hub.publish(sessionId, "meter", meter(sessionId));
        } catch (RuntimeException ex) {
            log.error("No se pudo registrar el consumo de la llamada {}", sessionId, ex);
            wallet.openIncident("USAGE_NOT_RECORDED", null, SUBJECT, String.valueOf(sessionId),
                    "Fallo al registrar un evento de consumo del proveedor: " + ex.getClass().getSimpleName());
        }
    }

    /** RN-23: al llegar al 90 % de la reserva, se amplía si hay saldo; si no, se corta la llamada. */
    private void enforceReserve(VoiceSession s) {
        if (!s.isLive() || "ADMIN".equals(roleOf(s))) {
            return;
        }
        CostEstimate measured = metering.measuredSoFar(SUBJECT, s.getId(), s.getPricingVersionId(), s.getPolicyId());
        BigDecimal reserved = wallet.reservedFor(SUBJECT, s.getId());
        if (!measured.available() || measured.totalMxn().compareTo(reserved.multiply(cutRatio)) < 0) {
            return;
        }
        try {
            CostEstimate perMinute = metering.estimate(s.getPricingVersionId(), s.getPolicyId(), perMinuteUnits);
            BigDecimal target = measured.totalMxn().add(reserveFor(perMinute.totalMxn(), minMinutes));
            BigDecimal limit = wallet.monthlyLimit(s.getUserId()).orElse(null);
            if (limit != null && wallet.capturedThisMonth(s.getUserId(), profiles.zoneOf(s.getUserId())).add(target)
                    .compareTo(limit) > 0) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "MONTHLY_LIMIT_REACHED", "Límite mensual.");
            }
            BigDecimal newReserved = tx.execute(st -> wallet.ensureReserved(s.getUserId(), SUBJECT, s.getId(), target));
            tx.executeWithoutResult(st -> sessions.findByIdForUpdate(s.getId())
                    .ifPresent(v -> v.reservationExtended(newReserved)));
        } catch (ApiException ex) {
            hub.publish(s.getId(), "warning", Map.of("message",
                    "Tu saldo reservado se agotó. La llamada terminará ahora; puedes recargar y volver a llamar."));
            requestEnd(s.getId(), "RESERVE_LIMIT");
        }
    }

    void handleTranscript(Long sessionId, String role, String text) {
        hub.publish(sessionId, "transcript", Map.of("role", role, "text", text));
        tx.executeWithoutResult(st -> sessions.findById(sessionId)
                .filter(VoiceSession::isStoreTranscript)
                .ifPresent(s -> transcripts.save(new VoiceTranscript(s.getId(), role, text, Instant.now(clock)))));
    }

    // ---- Fin de llamada -------------------------------------------------------------------------

    void requestEnd(Long sessionId, String reason) {
        Boolean changed = tx.execute(st -> sessions.findByIdForUpdate(sessionId).map(s -> s.beginEnding(reason)).orElse(false));
        if (!Boolean.TRUE.equals(changed)) {
            return;
        }
        hub.publish(sessionId, "ending", Map.of("reason", reason));
        String callId = tx.execute(st -> sessions.findById(sessionId).map(VoiceSession::getProviderCallId).orElse(null));
        if (callId != null) {
            safeHangup(callId);
        }
        // Breve espera para recibir los últimos eventos de consumo antes de liquidar.
        scheduler.schedule(() -> finalizeSession(sessionId), endGrace.toMillis(), TimeUnit.MILLISECONDS);
    }

    void finalizeSession(Long sessionId) {
        RealtimeVoiceProvider.Sideband sideband = sidebands.remove(sessionId);
        if (sideband != null) {
            try {
                sideband.close();
            } catch (RuntimeException ex) {
                log.debug("Cierre del canal lateral {}: {}", sessionId, ex.getMessage());
            }
        }
        try {
            SettlementView settlement = tx.execute(st -> {
                VoiceSession s = sessions.findByIdForUpdate(sessionId).orElseThrow();
                SettlementView v = metering.settle(s.getUserId(), SUBJECT, s.getId(), s.getPricingVersionId(),
                        s.getPolicyId());
                if (s.getStatus() != VoiceSession.Status.ENDED && s.getStatus() != VoiceSession.Status.FAILED) {
                    s.finish(Instant.now(clock));
                    int turns = (int) transcripts.findBySessionIdOrderByIdAsc(s.getId()).stream()
                            .filter(t -> "USER".equals(t.getRole())).count();
                    events.publishEvent(new com.englishos.voice.api.VoiceEvents.SessionEnded(s.getUserId(),
                            s.getPublicId(), s.connectedSeconds(Instant.now(clock)), turns, Instant.now(clock)));
                }
                return v;
            });
            hub.publish(sessionId, "ended", Map.of("settlement", settlement));
        } catch (RuntimeException ex) {
            log.error("No se pudo liquidar la llamada {}", sessionId, ex);
        } finally {
            warned.remove(sessionId);
            hub.complete(sessionId);
        }
    }

    private void failAndSettle(Long sessionId, String reason) {
        tx.executeWithoutResult(st -> {
            VoiceSession s = sessions.findByIdForUpdate(sessionId).orElseThrow();
            s.fail(reason, Instant.now(clock));
            // Sin consumo registrado el cargo es 0 y la reserva se libera completa.
            metering.settle(s.getUserId(), SUBJECT, s.getId(), s.getPricingVersionId(), s.getPolicyId());
        });
    }

    /** Duración máxima, aviso previo y desconexión del cliente (sin latidos). */
    void watchdog() {
        try {
            Instant now = Instant.now(clock);
            for (VoiceSession s : sessions.findByStatusIn(List.of(VoiceSession.Status.ACTIVE))) {
                long elapsed = s.connectedSeconds(now);
                if (elapsed >= s.getMaxDurationSeconds()) {
                    requestEnd(s.getId(), "MAX_DURATION");
                } else if (s.getLastHeartbeatAt() != null
                        && Duration.between(s.getLastHeartbeatAt(), now).compareTo(heartbeatTimeout) > 0) {
                    requestEnd(s.getId(), "CLIENT_LOST");
                } else if (elapsed >= s.getMaxDurationSeconds() - warnBefore.toSeconds() && warned.add(s.getId())) {
                    hub.publish(s.getId(), "warning", Map.of("message", "Queda menos de un minuto de llamada."));
                }
            }
        } catch (RuntimeException ex) {
            log.error("Fallo en la vigilancia de llamadas de voz", ex);
        }
    }

    // ---- Apoyo ----------------------------------------------------------------------------------

    private int affordableMinutes(BigDecimal available, BigDecimal spent, BigDecimal limit, BigDecimal perMinute) {
        if (perMinute == null || perMinute.signum() <= 0) {
            return maxMinutes;
        }
        BigDecimal budget = available;
        if (limit != null) {
            budget = budget.min(limit.subtract(spent).max(BigDecimal.ZERO));
        }
        BigDecimal perMinuteReserved = perMinute.multiply(reserveMargin);
        int affordable = budget.divide(perMinuteReserved, 0, RoundingMode.DOWN).intValue();
        return Math.min(maxMinutes, affordable);
    }

    private BigDecimal reserveFor(BigDecimal perMinute, int minutes) {
        return perMinute.multiply(reserveMargin).multiply(BigDecimal.valueOf(minutes)).setScale(2, RoundingMode.UP);
    }

    private String roleOf(VoiceSession s) {
        return wallet.reservedFor(SUBJECT, s.getId()).signum() == 0 ? "ADMIN" : "STUDENT";
    }

    private String instructions(Long userId, VoiceSession.Mode mode, String topic) {
        String level = levels.coreLevel(userId).map(Enum::name).orElse("unknown (assume A2)");
        String lang = "en".equals(profiles.explanationLanguage(userId)) ? "English" : "Spanish";
        List<String> recent = learnerErrors.recentPairs(userId, 5);
        String modeText = switch (mode) {
            case FREE -> "Free conversation. Let the student lead and keep them talking.";
            case GUIDED -> "Guided speaking practice of the grammar point in the topic.";
            case ROLEPLAY -> "Role-play the scenario in the topic. Stay in character; step out only to help.";
        };
        return promptTemplate
                .replace("{{level}}", level)
                .replace("{{explanationLanguage}}", lang)
                .replace("{{recentErrors}}", recent.isEmpty() ? "none recorded yet" : String.join("; ", recent))
                .replace("{{modeInstructions}}", modeText)
                .replace("{{topic}}", topic == null ? "student's choice" : "\"" + topic.replace("\"", "'") + "\"");
    }

    private VoiceModelInfo modelInfo(Long modelId) {
        MeteringApi.ModelRef m = metering.model(modelId);
        return new VoiceModelInfo(m.provider(), m.modelCode(), m.displayName(), m.simulated());
    }

    private VoiceMeterEvent meter(Long sessionId) {
        return tx.execute(st -> {
            VoiceSession s = sessions.findById(sessionId).orElseThrow();
            return new VoiceMeterEvent(s.connectedSeconds(Instant.now(clock)), s.getMaxDurationSeconds(),
                    metering.measuredSoFar(SUBJECT, s.getId(), s.getPricingVersionId(), s.getPolicyId()),
                    wallet.reservedFor(SUBJECT, s.getId()));
        });
    }

    private VoiceSession owned(CurrentUser user, String publicId) {
        return sessions.findByPublicId(publicId)
                .filter(s -> s.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("VOICE_SESSION_NOT_FOUND", "Llamada no encontrada."));
    }

    private VoiceSessionView toView(VoiceSession s) {
        List<VoiceTranscriptLine> lines = transcripts.findBySessionIdOrderByIdAsc(s.getId()).stream()
                .map(t -> new VoiceTranscriptLine(t.getRole(), t.getContent(), t.getCreatedAt())).toList();
        int studentTurns = (int) lines.stream().filter(l -> "USER".equals(l.role())).count();
        return new VoiceSessionView(s.getPublicId(), s.getMode().name(), s.getTopic(), s.getStatus().name(),
                s.getEndReason(), modelInfo(s.getModelId()), s.getMaxDurationSeconds(),
                s.connectedSeconds(Instant.now(clock)), s.getReservedMxn(),
                metering.measuredSoFar(SUBJECT, s.getId(), s.getPricingVersionId(), s.getPolicyId()),
                s.isStoreTranscript(), lines, studentTurns, metering.settlementOf(SUBJECT, s.getId()).orElse(null),
                s.getCreatedAt(), s.getConnectedAt(), s.getEndedAt());
    }

    private void safeHangup(String callId) {
        try {
            provider.hangup(callId);
        } catch (RuntimeException ex) {
            log.warn("No se pudo colgar en el proveedor: {}", ex.getMessage());
        }
    }
}
