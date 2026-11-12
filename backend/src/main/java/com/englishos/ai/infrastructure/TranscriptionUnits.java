package com.englishos.ai.infrastructure;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Unidad de la transcripción facturada por duración. Las tarifas se aplican "por millón de unidades"; midiendo el
 * audio en millonésimas de minuto, la tarifa que se captura es directamente el precio por minuto que publica el
 * proveedor (p. ej. 0.017 USD/min) y el cálculo es exacto salvo el redondeo a la millonésima de minuto (60 µs).
 */
final class TranscriptionUnits {

    static final String AUDIO_MICROMINUTES = "audio_microminutes";
    private static final BigDecimal MICROMINUTES_PER_SECOND = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal SECONDS_PER_MINUTE = BigDecimal.valueOf(60);

    private TranscriptionUnits() {
    }

    /** Segundos de audio (pueden traer decimales) → millonésimas de minuto, redondeadas a la unidad más cercana. */
    static long microminutes(BigDecimal seconds) {
        if (seconds == null || seconds.signum() <= 0) {
            return 0;
        }
        return seconds.multiply(MICROMINUTES_PER_SECOND).divide(SECONDS_PER_MINUTE, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
