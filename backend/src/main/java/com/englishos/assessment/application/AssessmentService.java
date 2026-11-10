package com.englishos.assessment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.englishos.assessment.domain.AssessmentResponse;
import com.englishos.assessment.domain.AssessmentSession;
import com.englishos.assessment.domain.Staircase;
import com.englishos.assessment.domain.UserSkillLevel;
import com.englishos.assessment.infrastructure.AssessmentResponseRepository;
import com.englishos.assessment.infrastructure.AssessmentSessionRepository;
import com.englishos.assessment.infrastructure.UserSkillLevelRepository;
import com.englishos.identity.api.LearnerProfiles;
import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.DiagnosticItemBank;
import com.englishos.learning.api.DiagnosticItemBank.ItemRef;
import com.englishos.learning.api.DiagnosticItemBank.ItemView;
import com.englishos.learning.api.LearningPlans;
import com.englishos.learning.api.Skill;
import com.englishos.shared.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Diagnóstico adaptativo (RF-ASM-1 a RF-ASM-5). */
@Service
public class AssessmentService {

    public static final String DISCLAIMER_ES =
            "Nivel estimado a partir de un diagnóstico breve. No es una certificación oficial del MCER.";
    public static final String DISCLAIMER_EN =
            "Estimated level from a short placement test. It is not an official CEFR certification.";

    private final AssessmentSessionRepository sessions;
    private final AssessmentResponseRepository responses;
    private final UserSkillLevelRepository skillLevels;
    private final DiagnosticItemBank bank;
    private final LearningPlans plans;
    private final LearnerProfiles learnerProfiles;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final Random random = new Random();
    private final Staircase.Params params = Staircase.Params.DEFAULT;

    AssessmentService(AssessmentSessionRepository sessions, AssessmentResponseRepository responses,
            UserSkillLevelRepository skillLevels, DiagnosticItemBank bank, LearningPlans plans,
            LearnerProfiles learnerProfiles, JsonMapper jsonMapper, Clock clock) {
        this.sessions = sessions;
        this.responses = responses;
        this.skillLevels = skillLevels;
        this.bank = bank;
        this.plans = plans;
        this.learnerProfiles = learnerProfiles;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public record SessionView(String sessionId, String status, int answered, int maxQuestions) {
    }

    /** Pregunta actual (sin solución) o {@code done = true} cuando el diagnóstico terminó. */
    public record NextQuestion(boolean done, int answered, int maxQuestions, ItemView item) {
    }

    public record SkillResult(Skill skill, CefrLevel level, String confidence, int evidence, int correct) {
    }

    public record Result(String sessionId, List<SkillResult> skills, CefrLevel planStartLevel, String disclaimerEs,
            String disclaimerEn, Instant completedAt) {
    }

    public record SkillLevelView(Skill skill, CefrLevel level, String confidence, int evidence, Instant updatedAt) {
    }

    // ---- Casos de uso -------------------------------------------------------------------------

    /** Reanuda el diagnóstico en curso o crea uno nuevo. */
    @Transactional
    public SessionView start(Long userId) {
        AssessmentSession session = sessions
                .findByUserIdAndStatusOrderByIdDesc(userId, AssessmentSession.Status.IN_PROGRESS).stream()
                .findFirst()
                .orElseGet(() -> sessions.save(AssessmentSession.start(userId, Staircase.ALGORITHM_VERSION,
                        jsonMapper.writeValueAsString(Staircase.initial(params)), Instant.now(clock))));
        return view(session, state(session));
    }

    @Transactional
    public NextQuestion next(Long userId, String sessionId) {
        AssessmentSession session = owned(userId, sessionId, true);
        Staircase.State state = state(session);
        if (session.getStatus() != AssessmentSession.Status.IN_PROGRESS) {
            return new NextQuestion(true, state.answered(), maxQuestions(), null);
        }
        if (session.getCurrentItemId() != null) {
            // Recargar la página no cambia la pregunta.
            return new NextQuestion(false, state.answered(), maxQuestions(), bank.view(session.getCurrentItemId()));
        }
        Set<Long> answered = responses.answeredItemIds(session.getId());
        List<ItemRef> pool = bank.publishedItems();
        while (true) {
            Skill skill = Staircase.nextSkill(state);
            if (skill == null) {
                complete(session, state);
                return new NextQuestion(true, state.answered(), maxQuestions(), null);
            }
            ItemRef item = pick(pool, answered, skill, state.track(skill).current());
            if (item == null) {
                state = Staircase.finish(state, skill); // el banco no tiene más ítems para esta habilidad
                session.advance(jsonMapper.writeValueAsString(state));
                continue;
            }
            session.present(item.id());
            return new NextQuestion(false, state.answered(), maxQuestions(), bank.view(item.id()));
        }
    }

    @Transactional
    public SessionView respond(Long userId, String sessionId, String itemPublicId, JsonNode answer) {
        AssessmentSession session = owned(userId, sessionId, true);
        if (session.getStatus() != AssessmentSession.Status.IN_PROGRESS) {
            throw ApiException.conflict("ASSESSMENT_FINISHED", "Este diagnóstico ya terminó.");
        }
        Long itemId = session.getCurrentItemId();
        if (itemId == null || !bank.view(itemId).publicId().equals(itemPublicId)) {
            throw ApiException.conflict("NOT_CURRENT_QUESTION",
                    "Esa no es la pregunta actual del diagnóstico. Recarga para continuar.");
        }
        ItemView item = bank.view(itemId);
        boolean correct = bank.isCorrect(itemId, answer);
        Instant now = Instant.now(clock);
        responses.save(AssessmentResponse.record(session.getId(), itemId, item.skill(), item.level(),
                jsonMapper.writeValueAsString(answer), correct, now));
        Staircase.State state = Staircase.record(state(session), item.skill(), item.level(), correct, params);
        session.advance(jsonMapper.writeValueAsString(state));
        if (state.isFinished()) {
            complete(session, state);
        }
        // No se revela si fue correcta: en un diagnóstico eso añade presión y sesga las respuestas siguientes.
        return view(session, state);
    }

    @Transactional(readOnly = true)
    public Result result(Long userId, String sessionId) {
        AssessmentSession session = owned(userId, sessionId, false);
        if (session.getStatus() != AssessmentSession.Status.COMPLETED) {
            throw ApiException.conflict("ASSESSMENT_NOT_FINISHED", "El diagnóstico aún no termina.");
        }
        return jsonMapper.readValue(session.getResultJson(), Result.class);
    }

    @Transactional(readOnly = true)
    public List<SkillLevelView> skillLevels(Long userId) {
        return skillLevels.findByUserId(userId).stream()
                .sorted(Comparator.comparing(UserSkillLevel::getSkill))
                .map(l -> new SkillLevelView(l.getSkill(), l.getLevel(), l.getConfidence().name(),
                        l.getEvidenceCount(), l.getUpdatedAt()))
                .toList();
    }

    // ---- Apoyo --------------------------------------------------------------------------------

    private void complete(AssessmentSession session, Staircase.State state) {
        Instant now = Instant.now(clock);
        List<SkillResult> results = new ArrayList<>();
        for (Staircase.SkillTrack track : state.tracks()) {
            Staircase.Estimate e = Staircase.estimate(track, params);
            results.add(new SkillResult(e.skill(), e.level(), e.confidence().name(), e.evidence(), e.correct()));
            UserSkillLevel.Key key = new UserSkillLevel.Key(session.getUserId(), e.skill());
            skillLevels.findById(key).ifPresentOrElse(l -> l.update(e, "DIAGNOSTIC", now),
                    () -> skillLevels.save(UserSkillLevel.of(session.getUserId(), e, "DIAGNOSTIC", now)));
        }
        // El plan parte del nivel más bajo entre vocabulario y gramática: son la base de las demás habilidades.
        CefrLevel start = CefrLevel.min(level(results, Skill.VOCABULARY), level(results, Skill.GRAMMAR));
        plans.createFromLevel(session.getUserId(), start, "DIAGNOSTIC");
        learnerProfiles.markOnboardingCompleted(session.getUserId());
        Result result = new Result(session.getPublicId(), results, start, DISCLAIMER_ES, DISCLAIMER_EN, now);
        session.complete(jsonMapper.writeValueAsString(result), now);
    }

    private static CefrLevel level(List<SkillResult> results, Skill skill) {
        return results.stream().filter(r -> r.skill() == skill).map(SkillResult::level).findFirst()
                .orElse(CefrLevel.A1);
    }

    /** Ítem no respondido de la habilidad, en el nivel actual o, si se agotó, en el nivel disponible más cercano. */
    private ItemRef pick(List<ItemRef> pool, Set<Long> answered, Skill skill, CefrLevel level) {
        List<ItemRef> candidates = pool.stream()
                .filter(i -> i.skill() == skill && !answered.contains(i.id()))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        int bestDistance = candidates.stream().mapToInt(i -> Math.abs(i.level().ordinal() - level.ordinal())).min()
                .orElseThrow();
        if (bestDistance > 1) {
            return null; // no evaluar lejos del nivel actual: la evidencia no serviría
        }
        List<ItemRef> nearest = candidates.stream()
                .filter(i -> Math.abs(i.level().ordinal() - level.ordinal()) == bestDistance)
                .sorted(Comparator.comparing(ItemRef::level)) // a igual distancia, primero el nivel inferior
                .toList();
        CefrLevel chosenLevel = nearest.getFirst().level();
        List<ItemRef> sameLevel = nearest.stream().filter(i -> i.level() == chosenLevel).toList();
        return sameLevel.get(random.nextInt(sameLevel.size()));
    }

    private AssessmentSession owned(Long userId, String sessionId, boolean lock) {
        return (lock ? sessions.findByPublicIdForUpdate(sessionId) : sessions.findByPublicId(sessionId))
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("ASSESSMENT_NOT_FOUND", "Diagnóstico no encontrado."));
    }

    private Staircase.State state(AssessmentSession session) {
        return jsonMapper.readValue(session.getStateJson(), Staircase.State.class);
    }

    private SessionView view(AssessmentSession s, Staircase.State state) {
        return new SessionView(s.getPublicId(), s.getStatus().name(), state.answered(), maxQuestions());
    }

    private int maxQuestions() {
        return params.maxItemsPerSkill() * Skill.values().length;
    }
}
