package com.englishos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * El perfil {@code demo} regala saldo simulado (recargas sin cobro). Eso solo es seguro si todo lo que se consume
 * también es simulado: con IA real o pagos reales, un alumno podría gastar dinero real con saldo ficticio. Por eso
 * la aplicación se niega a arrancar si el perfil demo se combina con proveedores reales.
 */
@Component
@Profile("demo")
class DemoModeGuard {

    DemoModeGuard(@Value("${app.ai.provider:fake}") String aiProvider,
            @Value("${app.ai.voice-provider:fake}") String voiceProvider,
            @Value("${app.payments.provider:none}") String paymentsProvider) {
        check(aiProvider, voiceProvider, paymentsProvider);
    }

    static void check(String aiProvider, String voiceProvider, String paymentsProvider) {
        if (!"fake".equals(aiProvider) || !"fake".equals(voiceProvider) || !"none".equals(paymentsProvider)) {
            throw new IllegalStateException("El perfil demo regala saldo simulado: solo puede usarse con IA y voz "
                    + "simuladas (AI_PROVIDER=fake, AI_VOICE_PROVIDER=fake) y sin pasarela de pago "
                    + "(PAYMENTS_PROVIDER=none). Valores actuales: " + aiProvider + ", " + voiceProvider + ", "
                    + paymentsProvider + ".");
        }
    }
}
