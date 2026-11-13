package com.englishos.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoModeGuardTest {

    @Test
    void demoIsAllowedOnlyWithEverythingSimulated() {
        assertThatCode(() -> DemoModeGuard.check("fake", "fake", "none")).doesNotThrowAnyException();
    }

    @Test
    void demoRefusesRealAiVoiceOrPayments() {
        assertThatThrownBy(() -> DemoModeGuard.check("openai", "fake", "none"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("AI_PROVIDER=fake");
        assertThatThrownBy(() -> DemoModeGuard.check("fake", "openai", "none"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DemoModeGuard.check("fake", "fake", "mercadopago"))
                .isInstanceOf(IllegalStateException.class);
    }
}
