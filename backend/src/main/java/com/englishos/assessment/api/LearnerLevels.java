package com.englishos.assessment.api;

import java.util.Optional;

import com.englishos.learning.api.CefrLevel;

/** Nivel estimado del alumno para adaptar al profesor IA (sin acceder a las tablas del diagnóstico). */
public interface LearnerLevels {

    /** Mínimo entre vocabulario y gramática, si el alumno hizo el diagnóstico. */
    Optional<CefrLevel> coreLevel(Long userId);

    /** Nivel estimado por habilidad (vacío si no hizo el diagnóstico). */
    java.util.Map<com.englishos.learning.api.Skill, CefrLevel> levelsBySkill(Long userId);
}
