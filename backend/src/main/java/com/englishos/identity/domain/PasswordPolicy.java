package com.englishos.identity.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Política de contraseñas (RF-IDN-1), alineada con NIST SP 800-63B: longitud mínima, sin reglas de composición
 * forzadas, y rechazo de contraseñas comunes o que contengan el correo.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    /** Lista corta de contraseñas comunes de ≥ 12 caracteres. Ampliable sin cambiar el contrato. */
    private static final Set<String> COMMON = Set.of(
            "123456789012", "1234567890123", "12345678901234", "password1234", "password12345",
            "passwordpassword", "qwertyuiop12", "qwertyuiopasdf", "qwerty123456", "iloveyou1234",
            "contraseña123", "contrasena123", "contrasena1234", "administrador", "admin1234567",
            "aaaaaaaaaaaa", "111111111111", "000000000000", "abc123456789", "abcdefghijkl",
            "letmein12345", "welcome12345", "englishos123", "englishos2026", "mexico123456");

    private PasswordPolicy() {
    }

    /** @return mensaje de error para el usuario, o vacío si la contraseña es aceptable. */
    public static Optional<String> validate(String password, String email) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            return Optional.of("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.");
        }
        if (password.length() > MAX_LENGTH) {
            return Optional.of("La contraseña no puede superar " + MAX_LENGTH + " caracteres.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) {
            return Optional.of("Esa contraseña es demasiado común. Elige otra.");
        }
        if (password.chars().distinct().count() < 4) {
            return Optional.of("La contraseña es demasiado repetitiva.");
        }
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@", 2)[0];
            if (local.length() >= 4 && lower.contains(local)) {
                return Optional.of("La contraseña no debe contener tu correo.");
            }
        }
        return Optional.empty();
    }
}
