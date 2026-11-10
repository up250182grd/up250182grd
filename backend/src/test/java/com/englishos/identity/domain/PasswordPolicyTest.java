package com.englishos.identity.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Correct-Horse-Battery-42", "una frase larga y fácil de recordar", "ñandú-azul-2026!"})
    void acceptsLongUncommonPasswords(String password) {
        assertThat(PasswordPolicy.validate(password, "ana@example.com")).isEmpty();
    }

    @Test
    void rejectsShortPasswords() {
        assertThat(PasswordPolicy.validate("Abc-12345", null)).hasValueSatisfying(m -> assertThat(m).contains("12"));
        assertThat(PasswordPolicy.validate(null, null)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"password1234", "PASSWORD1234", "123456789012", "contrasena123"})
    void rejectsCommonPasswordsCaseInsensitively(String password) {
        assertThat(PasswordPolicy.validate(password, null)).isPresent();
    }

    @Test
    void rejectsRepetitivePasswords() {
        assertThat(PasswordPolicy.validate("abababababab", null)).isPresent();
    }

    @Test
    void rejectsPasswordsContainingTheEmailLocalPart() {
        assertThat(PasswordPolicy.validate("mariana-segura-2026", "mariana@example.com")).isPresent();
    }

    @Test
    void rejectsOverlyLongPasswords() {
        assertThat(PasswordPolicy.validate("a1b2c3d4".repeat(17), null)).isPresent();
    }
}
