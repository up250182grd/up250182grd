package com.englishos.shared.privacy;

import java.time.Instant;

/** Contenido conversacional que se borra al cumplir su plazo de retención (RNF-11). */
public interface RetentionPolicy {

    String name();

    /** Borra el contenido creado antes de {@code before}; devuelve cuántos registros eliminó. */
    int purgeBefore(Instant before);
}
