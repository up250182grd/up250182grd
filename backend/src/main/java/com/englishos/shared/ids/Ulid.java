package com.englishos.shared.ids;

import java.security.SecureRandom;
import java.time.Clock;

/**
 * Generador de ULID (26 caracteres, Crockford Base32): 48 bits de tiempo en milisegundos + 80 bits aleatorios.
 * Se usa como identificador público para no exponer los IDs autoincrementales de la base de datos.
 */
public final class Ulid {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    public static String next() {
        return next(Clock.systemUTC().millis());
    }

    static String next(long epochMillis) {
        if (epochMillis < 0 || epochMillis > 0xFFFF_FFFF_FFFFL) {
            throw new IllegalArgumentException("Marca de tiempo fuera del rango de ULID");
        }
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);

        char[] out = new char[26];
        long time = epochMillis;
        for (int i = 9; i >= 0; i--) {
            out[i] = ALPHABET[(int) (time & 0x1F)];
            time >>>= 5;
        }
        // 80 bits aleatorios -> 16 caracteres de 5 bits.
        long hi = ((random[0] & 0xFFL) << 32) | ((random[1] & 0xFFL) << 24) | ((random[2] & 0xFFL) << 16)
                | ((random[3] & 0xFFL) << 8) | (random[4] & 0xFFL);
        long lo = ((random[5] & 0xFFL) << 32) | ((random[6] & 0xFFL) << 24) | ((random[7] & 0xFFL) << 16)
                | ((random[8] & 0xFFL) << 8) | (random[9] & 0xFFL);
        for (int i = 17; i >= 10; i--) {
            out[i] = ALPHABET[(int) (hi & 0x1F)];
            hi >>>= 5;
        }
        for (int i = 25; i >= 18; i--) {
            out[i] = ALPHABET[(int) (lo & 0x1F)];
            lo >>>= 5;
        }
        return new String(out);
    }

    public static boolean isValid(String value) {
        if (value == null || value.length() != 26) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (indexOf(value.charAt(i)) < 0) {
                return false;
            }
        }
        return indexOf(value.charAt(0)) <= 7; // el primer carácter solo codifica 3 bits
    }

    private static int indexOf(char c) {
        for (int i = 0; i < ALPHABET.length; i++) {
            if (ALPHABET[i] == c) {
                return i;
            }
        }
        return -1;
    }
}
