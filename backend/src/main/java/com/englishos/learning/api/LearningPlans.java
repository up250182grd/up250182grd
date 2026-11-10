package com.englishos.learning.api;

/** Creación del plan de estudio personalizado (RF-ASM-5). */
public interface LearningPlans {

    /** Crea un plan nuevo (y desactiva el anterior) que empieza en el nivel indicado. */
    void createFromLevel(Long userId, CefrLevel startLevel, String source);
}
