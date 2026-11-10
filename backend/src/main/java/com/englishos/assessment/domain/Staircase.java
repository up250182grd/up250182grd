package com.englishos.assessment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.Skill;

/**
 * Diagnóstico adaptativo por escalera, una por habilidad (RN-42). Funciones puras sobre un estado inmutable, de modo
 * que el algoritmo es verificable con pruebas unitarias y el estado se puede guardar como JSON entre preguntas.
 *
 * <ul>
 *   <li>Empieza en {@code start}. Tras {@code upAfter} aciertos seguidos sube un nivel; tras {@code downAfter}
 *       errores seguidos baja uno (sin salir de [{@code min}, {@code max}], el rango que cubre el banco de ítems).</li>
 *   <li>Un cambio de dirección cuenta como "inversión". La habilidad termina con {@code maxItemsPerSkill} ítems o
 *       {@code maxReversals} inversiones (convergencia).</li>
 *   <li>Nivel estimado = el más alto con ≥ {@code minItemsAtLevel} respuestas y tasa de acierto ≥ {@code passRate}.
 *       Si ninguno cumple, se informa {@code min} con confianza baja.</li>
 * </ul>
 * El resultado es una ESTIMACIÓN para orientar el plan, no una certificación del MCER.
 */
public final class Staircase {

    public static final String ALGORITHM_VERSION = "staircase-v1";

    private Staircase() {
    }

    public record Params(CefrLevel start, CefrLevel min, CefrLevel max, int upAfter, int downAfter,
            int maxItemsPerSkill, int maxReversals, int minItemsAtLevel, BigDecimal passRate) {

        /** Banco inicial: A1–B2. Ampliar {@code max} cuando haya ítems C1/C2 revisados. */
        public static final Params DEFAULT = new Params(CefrLevel.A2, CefrLevel.A1, CefrLevel.B2, 2, 2, 8, 3, 2,
                new BigDecimal("0.60"));
    }

    public record LevelStat(int answered, int correct) {
    }

    public record SkillTrack(Skill skill, CefrLevel current, int streakCorrect, int streakWrong, int reversals,
            int lastMove, int answered, boolean finished, Map<CefrLevel, LevelStat> stats) {

        public SkillTrack {
            stats = stats == null ? Map.of() : Map.copyOf(stats);
        }
    }

    public record State(List<SkillTrack> tracks, int turn) {

        public State {
            tracks = List.copyOf(tracks);
        }

        public SkillTrack track(Skill skill) {
            return tracks.stream().filter(t -> t.skill() == skill).findFirst().orElseThrow();
        }

        public boolean isFinished() {
            return tracks.stream().allMatch(SkillTrack::finished);
        }

        public int answered() {
            return tracks.stream().mapToInt(SkillTrack::answered).sum();
        }
    }

    public enum Confidence { LOW, MEDIUM, HIGH }

    public record Estimate(Skill skill, CefrLevel level, Confidence confidence, int evidence, int correct) {
    }

    public static State initial(Params p) {
        List<SkillTrack> tracks = new ArrayList<>();
        for (Skill s : Skill.values()) {
            tracks.add(new SkillTrack(s, p.start(), 0, 0, 0, 0, 0, false, Map.of()));
        }
        return new State(tracks, 0);
    }

    /** Siguiente habilidad a evaluar, alternando entre las que no han terminado (práctica intercalada). */
    public static Skill nextSkill(State state) {
        List<SkillTrack> tracks = state.tracks();
        for (int i = 0; i < tracks.size(); i++) {
            SkillTrack t = tracks.get((state.turn() + i) % tracks.size());
            if (!t.finished()) {
                return t.skill();
            }
        }
        return null;
    }

    public static State record(State state, Skill skill, CefrLevel itemLevel, boolean correct, Params p) {
        SkillTrack t = state.track(skill);
        if (t.finished()) {
            throw new IllegalStateException("La habilidad " + skill + " ya terminó");
        }
        Map<CefrLevel, LevelStat> stats = new EnumMap<>(CefrLevel.class);
        stats.putAll(t.stats());
        LevelStat prev = stats.getOrDefault(itemLevel, new LevelStat(0, 0));
        stats.put(itemLevel, new LevelStat(prev.answered() + 1, prev.correct() + (correct ? 1 : 0)));

        int streakCorrect = correct ? t.streakCorrect() + 1 : 0;
        int streakWrong = correct ? 0 : t.streakWrong() + 1;
        CefrLevel current = t.current();
        int lastMove = t.lastMove();
        int reversals = t.reversals();

        if (streakCorrect >= p.upAfter()) {
            if (current.compareTo(p.max()) < 0) {
                current = current.next();
                reversals += lastMove == -1 ? 1 : 0;
                lastMove = 1;
            }
            streakCorrect = 0;
        } else if (streakWrong >= p.downAfter()) {
            if (current.compareTo(p.min()) > 0) {
                current = current.previous();
                reversals += lastMove == 1 ? 1 : 0;
                lastMove = -1;
            }
            streakWrong = 0;
        }
        int answered = t.answered() + 1;
        boolean finished = answered >= p.maxItemsPerSkill() || reversals >= p.maxReversals();
        return replace(state, new SkillTrack(skill, current, streakCorrect, streakWrong, reversals, lastMove,
                answered, finished, stats), state.turn() + 1);
    }

    /** Termina una habilidad antes de tiempo (p. ej., el banco no tiene más ítems disponibles para ella). */
    public static State finish(State state, Skill skill) {
        SkillTrack t = state.track(skill);
        return replace(state, new SkillTrack(skill, t.current(), t.streakCorrect(), t.streakWrong(), t.reversals(),
                t.lastMove(), t.answered(), true, t.stats()), state.turn());
    }

    public static Estimate estimate(SkillTrack t, Params p) {
        CefrLevel best = null;
        for (Map.Entry<CefrLevel, LevelStat> e : t.stats().entrySet()) {
            LevelStat s = e.getValue();
            boolean enough = s.answered() >= p.minItemsAtLevel();
            boolean passed = s.answered() > 0 && BigDecimal.valueOf(s.correct())
                    .divide(BigDecimal.valueOf(s.answered()), 4, RoundingMode.HALF_UP).compareTo(p.passRate()) >= 0;
            if (enough && passed && (best == null || e.getKey().compareTo(best) > 0)) {
                best = e.getKey();
            }
        }
        int correct = t.stats().values().stream().mapToInt(LevelStat::correct).sum();
        Confidence confidence;
        if (best == null || t.answered() < 6) {
            confidence = Confidence.LOW;
        } else if (t.reversals() >= p.maxReversals()) {
            confidence = Confidence.HIGH;
        } else {
            confidence = Confidence.MEDIUM;
        }
        return new Estimate(t.skill(), best == null ? p.min() : best, confidence, t.answered(), correct);
    }

    private static State replace(State state, SkillTrack updated, int turn) {
        List<SkillTrack> tracks = new ArrayList<>();
        for (SkillTrack t : state.tracks()) {
            tracks.add(t.skill() == updated.skill() ? updated : t);
        }
        return new State(tracks, turn);
    }
}
