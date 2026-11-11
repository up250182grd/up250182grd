package com.englishos.wallet.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Asiento contable en construcción. Garantiza las invariantes de doble partida (RN-18) antes de tocar la base de
 * datos: ≥ 2 líneas, importes con 2 decimales y distintos de cero, y suma exactamente igual a cero.
 */
public final class LedgerPosting {

    public record Line(AccountRef account, BigDecimal amount) {
    }

    /** Cuenta del libro: código + dueño (0 = negocio). */
    public record AccountRef(AccountCode code, long ownerKey) {

        public static AccountRef user(AccountCode code, long userId) {
            if (!code.isUserAccount()) {
                throw new IllegalArgumentException(code + " no es una cuenta de usuario");
            }
            return new AccountRef(code, userId);
        }

        public static AccountRef business(AccountCode code) {
            if (code.isUserAccount()) {
                throw new IllegalArgumentException(code + " es una cuenta de usuario");
            }
            return new AccountRef(code, 0);
        }
    }

    private final List<Line> lines = new ArrayList<>();

    public LedgerPosting add(AccountRef account, BigDecimal amount) {
        if (amount.signum() != 0) {
            if (amount.stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("Los importes del libro deben tener 2 decimales: " + amount);
            }
            lines.add(new Line(account, amount.setScale(2, RoundingMode.UNNECESSARY)));
        }
        return this;
    }

    /** Mueve {@code amount} (> 0) de una cuenta a otra. */
    public LedgerPosting move(AccountRef from, AccountRef to, BigDecimal amount) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("El importe a mover debe ser positivo");
        }
        return add(from, amount.negate()).add(to, amount);
    }

    public List<Line> lines() {
        validate();
        return List.copyOf(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    private void validate() {
        if (lines.size() < 2) {
            throw new IllegalStateException("Un asiento necesita al menos dos líneas");
        }
        BigDecimal sum = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() != 0) {
            throw new IllegalStateException("Asiento desbalanceado: la suma es " + sum);
        }
    }
}
