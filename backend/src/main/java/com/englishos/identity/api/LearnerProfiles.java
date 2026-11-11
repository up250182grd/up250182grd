package com.englishos.identity.api;

import java.time.ZoneId;

/** Lo que otros módulos pueden consultar o cambiar del perfil del alumno, sin acceder a sus tablas. */
public interface LearnerProfiles {

    /** Zona horaria del alumno: los "días" de estudio y de dominio se cuentan en su hora local. */
    ZoneId zoneOf(Long userId);

    void markOnboardingCompleted(Long userId);

    /** Las funciones de IA de pago requieren correo verificado (RF-IDN-2). */
    boolean isEmailVerified(Long userId);

    /** Idioma preferido para las explicaciones ("es" | "en"). */
    String explanationLanguage(Long userId);

    /** Si el alumno permite guardar transcripciones de sus conversaciones (RN-37). */
    boolean storeTranscripts(Long userId);

    /** Meta diaria de estudio en minutos (para recomendar según el tiempo disponible). */
    int dailyGoalMinutes(Long userId);
}
