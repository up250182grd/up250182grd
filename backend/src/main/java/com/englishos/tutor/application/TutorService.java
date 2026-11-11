package com.englishos.tutor.application;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import tools.jackson.databind.json.JsonMapper;

import com.englishos.ai.api.LlmClient;
import com.englishos.ai.api.LlmClient.ChatRequest;
import com.englishos.ai.api.LlmClient.Message;
import com.englishos.ai.api.LlmClient.Role;
import com.englishos.assessment.api.LearnerLevels;
import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.api.LearnerProfiles;
import com.englishos.metering.api.MeteringApi;
import com.englishos.metering.api.MeteringApi.CostEstimate;
import com.englishos.metering.api.MeteringApi.SettlementView;
import com.englishos.shared.error.ApiException;
import com.englishos.tutor.domain.ChatMessage;
import com.englishos.tutor.domain.Conversation;
import com.englishos.tutor.domain.LearnerError;
import com.englishos.tutor.domain.ReplyFormat;
import com.englishos.tutor.infrastructure.ChatMessageRepository;
import com.englishos.tutor.infrastructure.ConversationRepository;
import com.englishos.tutor.infrastructure.LearnerErrorRepository;
import com.englishos.wallet.api.WalletApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Profesor IA por texto (RF-TUT). Cada turno tiene tres fases:
 * <ol>
 *   <li>Preparar (transacción): validar, reservar saldo suficiente para el peor caso del turno, guardar el mensaje.</li>
 *   <li>Llamar al proveedor (sin transacción abierta): streaming hacia el alumno.</li>
 *   <li>Registrar (transacción): respuesta, correcciones y el uso INFORMADO POR EL PROVEEDOR (nunca por el cliente).</li>
 * </ol>
 * El cobro se liquida una sola vez al cerrar la conversación (RN-12), con la tarifa y el factor fijados al iniciarla.
 */
@Service
public class TutorService {

    private static final Logger log = LoggerFactory.getLogger(TutorService.class);
    public static final String SUBJECT = "TEXT_CONVERSATION";
    static final String PROMPT_VERSION = "tutor-text-v1";

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final LearnerErrorRepository errors;
    private final MeteringApi metering;
    private final WalletApi wallet;
    private final LearnerProfiles profiles;
    private final LearnerLevels levels;
    private final LlmClient llm;
    private final JsonMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final String modelCode;
    private final int maxOutputTokens;
    private final int maxMessageChars;
    private final int maxTurns;
    private final int historyMessages;
    private final BigDecimal initialReserve;
    private final BigDecimal adminMonthlyCap;
    private final Duration idleTimeout;
    private final String promptTemplate;
    /** Turnos en curso (un turno a la vez por conversación). Suficiente para una sola instancia del backend. */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    TutorService(ConversationRepository conversations, ChatMessageRepository messages, LearnerErrorRepository errors,
            MeteringApi metering, WalletApi wallet, LearnerProfiles profiles, LearnerLevels levels, LlmClient llm,
            JsonMapper json, Clock clock, TransactionTemplate tx,
            @Value("${app.ai.text-model:fake-tutor-1}") String modelCode,
            @Value("${app.tutor.max-output-tokens:600}") int maxOutputTokens,
            @Value("${app.tutor.max-message-chars:2000}") int maxMessageChars,
            @Value("${app.tutor.max-turns:60}") int maxTurns,
            @Value("${app.tutor.history-messages:20}") int historyMessages,
            @Value("${app.tutor.initial-reserve-mxn:5.00}") BigDecimal initialReserve,
            @Value("${app.billing.admin-monthly-cap-mxn:1000.00}") BigDecimal adminMonthlyCap,
            @Value("${app.tutor.idle-timeout:30m}") Duration idleTimeout) throws IOException {
        this.conversations = conversations;
        this.messages = messages;
        this.errors = errors;
        this.metering = metering;
        this.wallet = wallet;
        this.profiles = profiles;
        this.levels = levels;
        this.llm = llm;
        this.json = json;
        this.clock = clock;
        this.tx = tx;
        this.modelCode = modelCode;
        this.maxOutputTokens = maxOutputTokens;
        this.maxMessageChars = maxMessageChars;
        this.maxTurns = maxTurns;
        this.historyMessages = historyMessages;
        this.initialReserve = initialReserve;
        this.adminMonthlyCap = adminMonthlyCap;
        this.idleTimeout = idleTimeout;
        try (InputStream in = new ClassPathResource("prompts/" + PROMPT_VERSION + ".txt").getInputStream()) {
            this.promptTemplate = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---- Vistas -------------------------------------------------------------------------------

    public record ModelInfo(String provider, String modelCode, String displayName, boolean simulated) {
    }

    public record MessageView(String role, String content, Instant createdAt) {
    }

    public record CorrectionView(String original, String correction, String explanationEs, String explanationEn,
            String category) {
    }

    public record ConversationSummary(String id, String mode, String topic, String status, int turns,
            Instant startedAt, Instant closedAt) {
    }

    public record ConversationView(String id, String mode, String topic, String status, int turns, int maxTurns,
            Instant startedAt, Instant closedAt, String closeReason, ModelInfo model, BigDecimal reservedMxn,
            CostEstimate measured, List<MessageView> messages, List<CorrectionView> corrections,
            Map<String, Long> correctionsByCategory, SettlementView settlement) {
    }

    public record PreparedTurn(Long conversationId, String publicId, Long userId, Long modelId, Long pricingVersionId,
            Long policyId, ChatRequest request) {
    }

    public record TurnResult(String reply, List<CorrectionView> corrections, int turns, CostEstimate measured,
            BigDecimal reservedMxn) {
    }

    // ---- Casos de uso -------------------------------------------------------------------------

    @Transactional
    public ConversationView start(CurrentUser user, Conversation.Mode mode, String topic) {
        if (!profiles.isEmailVerified(user.id())) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Confirma tu correo para usar al profesor IA.");
        }
        String cleanTopic = topic == null || topic.isBlank() ? null : topic.trim();
        if (cleanTopic != null && cleanTopic.length() > 200) {
            throw ApiException.badRequest("TOPIC_TOO_LONG", "El tema admite hasta 200 caracteres.");
        }
        MeteringApi.SessionPricing pricing = metering.pricingFor(user.id(), user.isAdmin(), llm.provider(), modelCode);
        Instant now = Instant.now(clock);
        Conversation c = conversations.saveAndFlush(Conversation.start(user.id(), mode, cleanTopic, PROMPT_VERSION,
                pricing.modelId(), pricing.pricingVersionId(), pricing.policyId(), now));
        if (user.isAdmin()) {
            checkAdminCap(user.id(), BigDecimal.ZERO);
        } else {
            checkMonthlyLimit(user.id(), initialReserve);
            wallet.ensureReserved(user.id(), SUBJECT, c.getId(), initialReserve);
        }
        return view(c);
    }

    @Transactional
    public PreparedTurn prepareTurn(CurrentUser user, String conversationId, String text) {
        String content = text == null ? "" : text.strip();
        if (content.isEmpty() || content.length() > maxMessageChars) {
            throw ApiException.badRequest("INVALID_MESSAGE",
                    "Escribe un mensaje de 1 a " + maxMessageChars + " caracteres.");
        }
        Conversation c = owned(user, conversationId, true);
        if (!c.isOpen()) {
            throw ApiException.conflict("CONVERSATION_CLOSED", "La conversación ya terminó. Empieza una nueva.");
        }
        if (c.getTurns() >= maxTurns) {
            throw ApiException.conflict("TURN_LIMIT_REACHED",
                    "Llegaste al máximo de mensajes de esta conversación. Ciérrala y empieza otra.");
        }
        if (!inFlight.add(c.getId())) {
            throw ApiException.conflict("TURN_IN_PROGRESS", "Espera a que el profesor termine de responder.");
        }
        try {
            List<Message> request = buildMessages(c, content);
            long promptChars = request.stream().mapToLong(m -> m.content().length()).sum();
            Map<String, Long> worstCase = new LinkedHashMap<>();
            worstCase.put("text_in", promptChars / 3 + 50); // estimación conservadora de tokens de entrada
            worstCase.put("text_out", (long) maxOutputTokens);
            CostEstimate next = metering.estimate(c.getPricingVersionId(), c.getPolicyId(), worstCase);
            CostEstimate soFar = metering.measuredSoFar(SUBJECT, c.getId(), c.getPricingVersionId(), c.getPolicyId());

            if (user.isAdmin()) {
                checkAdminCap(user.id(), next.available() ? soFar.totalMxn().add(next.totalMxn()) : BigDecimal.ZERO);
            } else {
                if (!next.available()) {
                    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRICING_UNAVAILABLE",
                            "No es posible calcular el costo en este momento (falta el tipo de cambio). Intenta más tarde.");
                }
                // Reserva para lo ya consumido + el peor caso del siguiente turno (RN-22, RN-23).
                BigDecimal target = soFar.totalMxn().add(next.totalMxn()).max(initialReserve)
                        .setScale(0, RoundingMode.UP).setScale(2);
                checkMonthlyLimit(user.id(), target);
                wallet.ensureReserved(user.id(), SUBJECT, c.getId(), target);
            }
            messages.save(new ChatMessage(c.getId(), "USER", content, Instant.now(clock)));
            return new PreparedTurn(c.getId(), c.getPublicId(), user.id(), c.getModelId(), c.getPricingVersionId(),
                    c.getPolicyId(), new ChatRequest(metering.model(c.getModelId()).modelCode(), request,
                            maxOutputTokens));
        } catch (RuntimeException ex) {
            inFlight.remove(c.getId());
            throw ex;
        }
    }

    /** Fase 2 y 3. {@code onVisibleDelta} recibe solo el texto para el alumno (sin el bloque de correcciones). */
    public TurnResult executeTurn(PreparedTurn turn, Consumer<String> onVisibleDelta) {
        try {
            ReplyFormat.StreamFilter filter = new ReplyFormat.StreamFilter();
            LlmClient.ChatResult result = llm.stream(turn.request(), delta -> {
                String visible = filter.accept(delta);
                if (!visible.isEmpty()) {
                    onVisibleDelta.accept(visible);
                }
            });
            String rest = filter.flush();
            if (!rest.isEmpty()) {
                onVisibleDelta.accept(rest);
            }
            ReplyFormat.Parsed parsed = ReplyFormat.parse(result.text(), json);
            return tx.execute(status -> record(turn, result, parsed));
        } finally {
            inFlight.remove(turn.conversationId());
        }
    }

    private TurnResult record(PreparedTurn turn, LlmClient.ChatResult result, ReplyFormat.Parsed parsed) {
        Instant now = Instant.now(clock);
        // El uso se registra aunque la conversación se haya cerrado mientras tanto: el consumo ocurrió.
        metering.recordUsage(new MeteringApi.UsageInput(turn.userId(), SUBJECT, turn.conversationId(), turn.modelId(),
                turn.pricingVersionId(), result.responseId(), result.units(), result.dataSource()));
        Conversation c = conversations.findById(turn.conversationId()).orElseThrow();
        messages.save(new ChatMessage(c.getId(), "ASSISTANT", parsed.visibleText(), now));
        List<CorrectionView> corrections = new ArrayList<>();
        for (ReplyFormat.Correction corr : parsed.corrections()) {
            errors.save(LearnerError.of(turn.userId(), c.getId(), corr, now));
            corrections.add(toView(corr));
        }
        c.recordTurn(now);
        CostEstimate measured = metering.measuredSoFar(SUBJECT, c.getId(), c.getPricingVersionId(), c.getPolicyId());
        if (!c.isOpen()) {
            // Cerrada durante el turno (inactividad): la liquidación ya existe; el excedente se registra como incidencia.
            wallet.openIncident("USAGE_AFTER_CLOSE", c.getUserId(), SUBJECT, String.valueOf(c.getId()),
                    "Se registró uso del proveedor después de liquidar la conversación; requiere conciliación.");
        }
        return new TurnResult(parsed.visibleText(), corrections, c.getTurns(), measured,
                wallet.reservedFor(SUBJECT, c.getId()));
    }

    @Transactional
    public ConversationView close(CurrentUser user, String conversationId) {
        Conversation c = owned(user, conversationId, true);
        if (inFlight.contains(c.getId())) {
            throw ApiException.conflict("TURN_IN_PROGRESS", "Espera a que el profesor termine de responder.");
        }
        closeAndSettle(c, "USER");
        return view(c);
    }

    /** Cierra conversaciones inactivas y liquida su consumo (RN-12: 30 min sin actividad). */
    @Scheduled(fixedDelayString = "${app.tutor.idle-check:PT5M}", initialDelayString = "${app.tutor.idle-check:PT5M}")
    public void closeIdle() {
        Instant before = Instant.now(clock).minus(idleTimeout);
        for (String id : conversations.findIdleOpen(before)) {
            try {
                tx.executeWithoutResult(status -> conversations.findByPublicIdForUpdate(id)
                        .filter(c -> c.isOpen() && !inFlight.contains(c.getId()))
                        .ifPresent(c -> closeAndSettle(c, "IDLE")));
            } catch (RuntimeException ex) {
                log.error("No se pudo cerrar por inactividad la conversación {}", id, ex);
            }
        }
    }

    private void closeAndSettle(Conversation c, String reason) {
        if (c.isOpen()) {
            c.close(reason, Instant.now(clock));
        }
        metering.settle(c.getUserId(), SUBJECT, c.getId(), c.getPricingVersionId(), c.getPolicyId());
    }

    @Transactional(readOnly = true)
    public ConversationView get(CurrentUser user, String conversationId) {
        return view(owned(user, conversationId, false));
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(CurrentUser user) {
        return conversations.findRecentByUser(user.id(), PageRequest.of(0, 30)).stream()
                .map(c -> new ConversationSummary(c.getPublicId(), c.getMode().name(), c.getTopic(),
                        c.getStatus().name(), c.getTurns(), c.getStartedAt(), c.getClosedAt()))
                .toList();
    }

    /** Errores recientes del alumno (para el panel y para personalizar al profesor). */
    @Transactional(readOnly = true)
    public List<CorrectionView> recentErrors(Long userId, int limit) {
        return errors.findRecentByUser(userId, PageRequest.of(0, limit)).stream()
                .map(e -> toView(e.toCorrection())).toList();
    }

    // ---- Apoyo --------------------------------------------------------------------------------

    private List<Message> buildMessages(Conversation c, String newUserMessage) {
        List<Message> out = new ArrayList<>();
        out.add(new Message(Role.SYSTEM, systemPrompt(c)));
        List<ChatMessage> history = new ArrayList<>(messages.findLatest(c.getId(), PageRequest.of(0, historyMessages)));
        Collections.reverse(history);
        for (ChatMessage m : history) {
            out.add(new Message("USER".equals(m.getRole()) ? Role.USER : Role.ASSISTANT, m.getContent()));
        }
        out.add(new Message(Role.USER, newUserMessage));
        return out;
    }

    String systemPrompt(Conversation c) {
        String level = levels.coreLevel(c.getUserId()).map(Enum::name).orElse("unknown (assume A2)");
        String lang = "en".equals(profiles.explanationLanguage(c.getUserId())) ? "English" : "Spanish";
        String recent = errors.findRecentByUser(c.getUserId(), PageRequest.of(0, 5)).stream()
                .map(e -> "\"" + e.getOriginal() + "\" -> \"" + e.getCorrection() + "\"")
                .collect(Collectors.joining("; "));
        String modeText = switch (c.getMode()) {
            case FREE -> "Free conversation. Let the student lead; correct gently and summarise mistakes in the JSON.";
            case GUIDED -> "Guided practice of the grammar point in the topic. Elicit sentences that use it.";
            case ROLEPLAY -> "Role-play the scenario in the topic. Stay in character; step out only to correct.";
        };
        // Los datos del alumno van como datos dentro de la plantilla; el texto libre (tema) se recorta y se cita.
        String topic = c.getTopic() == null ? "student's choice" : "\"" + c.getTopic().replace("\"", "'") + "\"";
        return promptTemplate
                .replace("{{level}}", level)
                .replace("{{explanationLanguage}}", lang)
                .replace("{{recentErrors}}", recent.isEmpty() ? "none recorded yet" : recent)
                .replace("{{modeInstructions}}", modeText)
                .replace("{{topic}}", topic);
    }

    private void checkMonthlyLimit(Long userId, BigDecimal reservationTarget) {
        var limit = wallet.monthlyLimit(userId);
        if (limit.isPresent()) {
            ZoneId zone = profiles.zoneOf(userId);
            BigDecimal spent = wallet.capturedThisMonth(userId, zone);
            if (spent.add(reservationTarget).compareTo(limit.get()) > 0) {
                throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "MONTHLY_LIMIT_REACHED",
                        "Alcanzarías tu límite de gasto mensual (" + limit.get() + " MXN). Puedes ajustarlo en tu billetera.");
            }
        }
    }

    private void checkAdminCap(Long userId, BigDecimal pending) {
        BigDecimal spent = metering.settledThisMonth(userId, profiles.zoneOf(userId));
        if (spent.add(pending).compareTo(adminMonthlyCap) > 0) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "ADMIN_MONTHLY_CAP_REACHED",
                    "Se alcanzó el tope mensual de consumo interno del administrador (" + adminMonthlyCap + " MXN).");
        }
    }

    private Conversation owned(CurrentUser user, String publicId, boolean lock) {
        return (lock ? conversations.findByPublicIdForUpdate(publicId) : conversations.findByPublicId(publicId))
                .filter(c -> c.getUserId().equals(user.id()))
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversación no encontrada."));
    }

    private ConversationView view(Conversation c) {
        MeteringApi.ModelRef model = metering.model(c.getModelId());
        List<MessageView> msgs = messages.findByConversationIdOrderByIdAsc(c.getId()).stream()
                .map(m -> new MessageView(m.getRole(), m.getContent(), m.getCreatedAt())).toList();
        List<CorrectionView> corrections = errors.findByConversationIdOrderByIdAsc(c.getId()).stream()
                .map(e -> toView(e.toCorrection())).toList();
        Map<String, Long> byCategory = corrections.stream()
                .collect(Collectors.groupingBy(CorrectionView::category, LinkedHashMap::new, Collectors.counting()));
        SettlementView settlement = metering.settlementOf(SUBJECT, c.getId()).orElse(null);
        return new ConversationView(c.getPublicId(), c.getMode().name(), c.getTopic(), c.getStatus().name(),
                c.getTurns(), maxTurns, c.getStartedAt(), c.getClosedAt(), c.getCloseReason(),
                new ModelInfo(model.provider(), model.modelCode(), model.displayName(), model.simulated()),
                wallet.reservedFor(SUBJECT, c.getId()),
                metering.measuredSoFar(SUBJECT, c.getId(), c.getPricingVersionId(), c.getPolicyId()), msgs,
                corrections, byCategory, settlement);
    }

    private static CorrectionView toView(ReplyFormat.Correction c) {
        return new CorrectionView(c.original(), c.correction(), c.explanationEs(), c.explanationEn(), c.category());
    }
}
