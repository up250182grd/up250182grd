package com.englishos.wallet.api;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Operaciones de billetera que usan otros módulos (medición, tutor, voz). Todas son idempotentes por sujeto
 * (p. ej. una conversación) y se ejecutan dentro de la transacción del llamador.
 */
public interface WalletApi {

    /** Desglose de un cargo ya calculado por la calculadora normativa. total = direct + markup + tax. */
    record ChargeLines(BigDecimal directCost, BigDecimal markup, BigDecimal tax, BigDecimal total) {
    }

    record SettleResult(Long captureTransactionId, BigDecimal captured, BigDecimal released, boolean overdraftIncident) {
    }

    /**
     * Garantiza que la reserva del sujeto sea al menos {@code targetTotal}, apartando la diferencia del saldo
     * disponible. Lanza 402 INSUFFICIENT_FUNDS si no alcanza (nunca deja saldo negativo al reservar).
     */
    BigDecimal ensureReserved(Long userId, String subjectType, Long subjectId, BigDecimal targetTotal);

    BigDecimal reservedFor(String subjectType, Long subjectId);

    /** Cobra al cliente el total (primero de la reserva, el excedente del disponible) y libera lo no usado. */
    SettleResult settleCustomer(Long userId, String subjectType, Long subjectId, ChargeLines charge);

    /** Consumo del administrador: gasto interno al costo, sin pasar por su saldo ni por pasarela (RN-8). */
    Long recordInternalUsage(Long userId, String subjectType, Long subjectId, BigDecimal directCost);

    BigDecimal available(Long userId);

    BigDecimal capturedThisMonth(Long userId, ZoneId zone);

    Optional<BigDecimal> monthlyLimit(Long userId);

    void openIncident(String type, Long userId, String refType, String refId, String description);

    record IncidentView(Long id, String type, Long userId, String refType, String refId, String description,
            java.time.Instant createdAt) {
    }

    /** Saldos de las cuentas del negocio (ingresos, recargo, impuestos, gasto interno, contrapartidas). */
    java.util.Map<String, BigDecimal> businessBalances();

    java.util.List<IncidentView> openIncidents();

    /** Invariantes del libro: Σ global = 0 y caché de saldo = Σ líneas. Vacío si todo cuadra. */
    java.util.List<String> verifyLedger();
}
