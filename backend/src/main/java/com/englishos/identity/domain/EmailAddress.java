package com.englishos.identity.domain;

import java.util.Locale;

/** Normalización de correos: se recortan espacios y se pasa a minúsculas para garantizar unicidad. */
public final class EmailAddress {

    private EmailAddress() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
